(ns reitit.ring.coercion
  (:require [clojure.walk :as walk]
            [spec-tools.core :as st]
            [reitit.ring.middleware :as middleware]
            [reitit.ring.coercion.protocol :as protocol]
            [reitit.ring :as ring]
            [reitit.impl :as impl]))

#_(defn get-apidocs [coercion spec info]
    (protocol/get-apidocs coercion spec info))

;;
;; coercer
;;

(defrecord ParameterCoercion [in style keywordize? open?])

(def ring-parameter-coercion
  {:query (->ParameterCoercion :query-params :string true true)
   :body (->ParameterCoercion :body-params :string false true)
   :form (->ParameterCoercion :form-params :string true true)
   :header (->ParameterCoercion :header-params :string true true)
   :path (->ParameterCoercion :path-params :string true true)})

(defn request-coercion-failed! [result coercion value in request]
  (throw
    (ex-info
      (str "Request coercion failed: " (pr-str result))
      (merge
        (into {} result)
        {:type ::request-coercion
         :coercion coercion
         :value value
         :in [:request in]
         :request request}))))

(defn response-coercion-failed! [result coercion value request response]
  (throw
    (ex-info
      (str "Response coercion failed: " (pr-str result))
      (merge
        (into {} result)
        {:type ::response-coercion
         :coercion coercion
         :value value
         :in [:response :body]
         :request request
         :response response}))))

(defn request-coercer [coercion type model]
  (if coercion
    (let [{:keys [keywordize? open? in style]} (ring-parameter-coercion type)
          transform (comp (if keywordize? walk/keywordize-keys identity) in)
          model (if open? (protocol/make-open coercion model) model)
          coercer (protocol/request-coercer coercion style model)]
      (fn [request]
        (let [value (transform request)
              format (some-> request :muuntaja/request :format)
              result (coercer value format)]
          (if (protocol/error? result)
            (request-coercion-failed! result coercion value in request)
            result))))))

#_(defn muuntaja-response-format [request response]
    (or (-> response :muuntaja/content-type)
        (some-> request :muuntaja/response :format)))

(defn response-coercer [coercion model {:keys [extract-response-format]
                                        :or {extract-response-format (constantly nil)}}]
  (if coercion
    (let [coercer (protocol/response-coercer coercion model)]
      (fn [request response]
        (let [format (extract-response-format request response)
              value (:body response)
              result (coercer value format)]
          (if (protocol/error? result)
            (response-coercion-failed! result coercion value request response)
            result))))))

;;
;; middleware
;;

(defn- coerce-parameters [coercers request]
  (reduce-kv
    (fn [acc k coercer]
      (impl/fast-assoc acc k (coercer request)))
    {}
    coercers))

(defn- coerce-response [coercers request response]
  (if response
    (if-let [coercer (or (coercers (:status response)) (coercers :default))]
      (impl/fast-assoc response :body (coercer request response)))))

(defn ^:no-doc request-coercers [coercion parameters]
  (->> (for [[k v] parameters
             :when v]
         [k (request-coercer coercion k v)])
       (into {})))

(defn ^:no-doc response-coercers [coercion responses opts]
  (->> (for [[status {:keys [schema]}] responses :when schema]
         [status (response-coercer coercion schema opts)])
       (into {})))

(defn wrap-coerce-parameters
  "Pluggable request coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :parameters from route meta, otherwise will do nothing."
  [handler]
  (fn
    ([request]
     (let [method (:request-method request)
           match (ring/get-match request)
           parameters (-> match :result method :meta :parameters)
           coercion (-> match :meta :coercion)]
       (if (and coercion parameters)
         (let [coercers (request-coercers coercion parameters)
               coerced (coerce-parameters coercers request)]
           (handler (impl/fast-assoc request :parameters coerced)))
         (handler request))))
    ([request respond raise]
     (let [method (:request-method request)
           match (ring/get-match request)
           parameters (-> match :result method :meta :parameters)
           coercion (-> match :meta :coercion)]
       (if (and coercion parameters)
         (let [coercers (request-coercers coercion parameters)
               coerced (coerce-parameters coercers request)]
           (handler (impl/fast-assoc request :parameters coerced) respond raise)))))))

(def gen-wrap-coerce-parameters
  "Generator for pluggable request coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :parameters from route meta, otherwise does not mount."
  (middleware/create
    {:name ::coerce-parameters
     :gen (fn [{:keys [parameters coercion]} _]
            (if (and coercion parameters)
              (let [coercers (request-coercers coercion parameters)]
                (fn [handler]
                  (fn
                    ([request]
                     (let [coerced (coerce-parameters coercers request)]
                       (handler (impl/fast-assoc request :parameters coerced))))
                    ([request respond raise]
                     (let [coerced (coerce-parameters coercers request)]
                       (handler (impl/fast-assoc request :parameters coerced) respond raise))))))))}))

(defn wrap-coerce-response
  "Pluggable response coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :responses from route meta, otherwise will do nothing."
  [handler]
  (fn
    ([request]
     (let [response (handler request)
           method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :meta :responses)
           coercion (-> match :meta :coercion)
           opts (-> match :meta :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)]
           (coerce-response coercers request response))
         response)))
    ([request respond raise]
     (let [method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :meta :responses)
           coercion (-> match :meta :coercion)
           opts (-> match :meta :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)]
           (handler request #(respond (coerce-response coercers request %))))
         (handler request respond raise))))))

(def gen-wrap-coerce-response
  "Generator for pluggable response coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :responses from route meta, otherwise does not mount."
  (middleware/create
    {:name ::coerce-response
     :gen (fn [{:keys [responses coercion opts]} _]
            (if (and coercion responses)
              (let [coercers (response-coercers coercion responses opts)]
                (fn [handler]
                  (fn
                    ([request]
                     (coerce-response coercers request (handler request)))
                    ([request respond raise]
                     (handler request #(respond (coerce-response coercers request %)) raise)))))))}))
