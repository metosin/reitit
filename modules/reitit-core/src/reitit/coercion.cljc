(ns reitit.coercion
  (:require [clojure.walk :as walk]
            [spec-tools.core :as st]
            [reitit.ring :as ring]
            [reitit.impl :as impl]))

;;
;; Protocol
;;

(defprotocol Coercion
  "Pluggable coercion protocol"
  (-get-name [this] "Keyword name for the coercion")
  (-get-apidocs [this model data] "???")
  (-compile-model [this model name] "Compiles a model")
  (-open-model [this model] "Returns a new model which allows extra keys in maps")
  (-encode-error [this error] "Converts error in to a serializable format")
  (-request-coercer [this type model] "Returns a `value format => value` request coercion function")
  (-response-coercer [this model] "Returns a `value format => value` response coercion function"))

(defrecord CoercionError [])

(defn error? [x]
  (instance? CoercionError x))

;;
;; api-docs
;;

#_(defn get-apidocs [coercion spec info]
    (protocol/get-apidocs coercion spec info))

;;
;; coercer
;;

(defrecord ParameterCoercion [in style keywordize? open?])

(def ^:no-doc ring-parameter-coercion
  {:query (->ParameterCoercion :query-params :string true true)
   :body (->ParameterCoercion :body-params :body false false)
   :form (->ParameterCoercion :form-params :string true true)
   :header (->ParameterCoercion :header-params :string true true)
   :path (->ParameterCoercion :path-params :string true true)})

(defn ^:no-doc request-coercion-failed! [result coercion value in request]
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

(defn ^:no-doc response-coercion-failed! [result coercion value request response]
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

;; TODO: support faster key walking, walk/keywordize-keys is quite slow...
(defn request-coercer [coercion type model {:keys [extract-request-format]
                                            :or {extract-request-format (constantly nil)}}]
  (if coercion
    (let [{:keys [keywordize? open? in style]} (ring-parameter-coercion type)
          transform (comp (if keywordize? walk/keywordize-keys identity) in)
          model (if open? (-open-model coercion model) model)
          coercer (-request-coercer coercion style model)]
      (fn [request]
        (let [value (transform request)
              format (extract-request-format request)
              result (coercer value format)]
          (if (error? result)
            (request-coercion-failed! result coercion value in request)
            result))))))

(defn response-coercer [coercion model {:keys [extract-response-format]
                                        :or {extract-response-format (constantly nil)}}]
  (if coercion
    (let [coercer (-response-coercer coercion model)]
      (fn [request response]
        (let [format (extract-response-format request response)
              value (:body response)
              result (coercer value format)]
          (if (error? result)
            (response-coercion-failed! result coercion value request response)
            result))))))

(defn encode-error [data]
  (-> data
      (dissoc :request :response)
      (update :coercion -get-name)
      (->> (-encode-error (:coercion data)))))

(defn coerce-request [coercers request]
  (reduce-kv
    (fn [acc k coercer]
      (impl/fast-assoc acc k (coercer request)))
    {}
    coercers))

(defn coerce-response [coercers request response]
  (if response
    (if-let [coercer (or (coercers (:status response)) (coercers :default))]
      (impl/fast-assoc response :body (coercer request response)))))

(defn request-coercers [coercion parameters opts]
  (->> (for [[k v] parameters
             :when v]
         [k (request-coercer coercion k v opts)])
       (into {})))

(defn response-coercers [coercion responses opts]
  (->> (for [[status {:keys [schema]}] responses :when schema]
         [status (response-coercer coercion schema opts)])
       (into {})))

;;
;; integration
;;

(defn compile-request-coercers [[p {:keys [parameters coercion]}] opts]
  (if (and parameters coercion)
    (request-coercers coercion parameters opts)))

(defn coerce! [match]
  (coerce-request (:result match) {:path-params (:params match)}))
