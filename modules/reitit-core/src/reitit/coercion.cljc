(ns reitit.coercion
  (:require [clojure.walk :as walk]
            [reitit.impl :as impl])
  #?(:clj
     (:import (java.io Writer))))

;;
;; Protocol
;;

(defprotocol Coercion
  "Pluggable coercion protocol"
  (-get-name [this] "Keyword name for the coercion")
  (-get-options [this] "Coercion options")
  (-get-apidocs [this specification data] "Returns api documentation")
  (-compile-model [this model name] "Compiles a model")
  (-open-model [this model] "Returns a new model which allows extra keys in maps")
  (-encode-error [this error] "Converts error in to a serializable format")
  (-request-coercer [this type model] "Returns a `value format => value` request coercion function")
  (-response-coercer [this model] "Returns a `value format => value` response coercion function"))

#?(:clj
   (defmethod print-method ::coercion [coercion ^Writer w]
     (.write w (str "#Coercion{:name " (-get-name coercion) "}"))))

(defrecord CoercionError [])

(defn error? [x]
  (instance? CoercionError x))

;;
;; coercer
;;

(defrecord ParameterCoercion [in style keywordize? open?])

(def ^:no-doc default-parameter-coercion
  {:query (->ParameterCoercion :query-params :string true true)
   :body (->ParameterCoercion :body-params :body false false)
   :request (->ParameterCoercion :body-params :request false false)
   :form (->ParameterCoercion :form-params :string true true)
   :header (->ParameterCoercion :headers :string true true)
   :path (->ParameterCoercion :path-params :string true true)})

(defn ^:no-doc request-coercion-failed! [result coercion value in request serialize-failed-result]
  (throw
   (ex-info
    (if serialize-failed-result
      (str "Request coercion failed: " (pr-str result))
      "Request coercion failed")
    (-> {}
        transient
        (as-> $ (reduce conj! $ result))
        (assoc! :type ::request-coercion)
        (assoc! :coercion coercion)
        (assoc! :value value)
        (assoc! :in [:request in])
        (assoc! :request request)
        persistent!))))

(defn ^:no-doc response-coercion-failed! [result coercion value request response serialize-failed-result]
  (throw
   (ex-info
    (if serialize-failed-result
      (str "Response coercion failed: " (pr-str result))
      "Response coercion failed")
    (-> {}
        transient
        (as-> $ (reduce conj! $ result))
        (assoc! :type ::response-coercion)
        (assoc! :coercion coercion)
        (assoc! :value value)
        (assoc! :in [:response :body])
        (assoc! :request request)
        (assoc! :response response)
        persistent!))))

(defn extract-request-format-default [request]
  (-> request :muuntaja/request :format))

;; TODO: support faster key walking, walk/keywordize-keys is quite slow...
(defn request-coercer [coercion type model {::keys [extract-request-format parameter-coercion serialize-failed-result]
                                            :or {extract-request-format extract-request-format-default
                                                 parameter-coercion default-parameter-coercion}}]
  (if coercion
    (if-let [{:keys [keywordize? open? in style]} (parameter-coercion type)]
      (let [transform (comp (if keywordize? walk/keywordize-keys identity) in)
            ->open (if open? #(-open-model coercion %) identity)
            format-coercer-pairs (if (= :request style)
                                   (for [[format schema] (:content model)]
                                     [format (-request-coercer coercion :body (->open schema))])
                                   [[:default (-request-coercer coercion style (->open model))]])
            format->coercer (some->> format-coercer-pairs
                                     (filter second)
                                     (seq)
                                     (into {}))]
        (when format->coercer
          (fn [request]
            (let [value (transform request)
                  format (extract-request-format request)
                  coercer (or (format->coercer format)
                              (format->coercer :default)
                              (fn [value _format] value))
                  result (coercer value format)]
              (if (error? result)
                (request-coercion-failed! result coercion value in request serialize-failed-result)
                result))))))))

(defn extract-response-format-default [request _]
  (-> request :muuntaja/response :format))

(defn response-coercer [coercion {:keys [content body]} {:keys [extract-response-format serialize-failed-result]
                                                         :or {extract-response-format extract-response-format-default}}]
  (if coercion
    (let [per-format-coercers (some->> (for [[format schema] content]
                                         [format (-response-coercer coercion schema)])
                                       (filter second)
                                       (seq)
                                       (into {}))
          default (when body (-response-coercer coercion body))]
      (when (or per-format-coercers default)
        (fn [request response]
          (let [format (extract-response-format request response)
                value (:body response)
                coercer (get per-format-coercers format (or default (fn [value _format] value)))
                result (coercer value format)]
            (if (error? result)
              (response-coercion-failed! result coercion value request response serialize-failed-result)
              result)))))))

(defn encode-error [data]
  (-> data
      (dissoc :request :response)
      (update :coercion -get-name)
      (->> (-encode-error (:coercion data)))))

(defn coerce-request [coercers request]
  (reduce-kv
   (fn [acc k coercer]
     (impl/fast-assoc acc k (coercer request)))
   {} coercers))

(defn coerce-response [coercers request response]
  (if response
    (if-let [coercer (or (coercers (:status response)) (coercers :default))]
      (impl/fast-assoc response :body (coercer request response))
      response)))

(defn request-coercers [coercion parameters opts]
  (some->> (for [[k v] parameters
                 :when v]
             [k (request-coercer coercion k v opts)])
           (filter second)
           (seq)
           (into {})))

(defn response-coercers [coercion responses opts]
  (some->> (for [[status model] responses]
             [status (response-coercer coercion model opts)])
           (filter second)
           (seq)
           (into {})))

;;
;; api-docs
;;

(defn get-apidocs [coercion specification data]
  (let [swagger-parameter {:query :query
                           :body :body
                           :form :formData
                           :header :header
                           :path :path
                           :multipart :formData}]
    (case specification
      :openapi (-get-apidocs coercion specification data)
      :swagger (->> (update
                     data
                     :parameters
                     (fn [parameters]
                       (->> parameters
                            (map (fn [[k v]] [(swagger-parameter k) v]))
                            (filter first)
                            (into {}))))
                    (-get-apidocs coercion specification)))))


;;
;; integration
;;

(defn compile-request-coercers
  "A router :compile implementation which reads the `:parameters`
  and `:coercion` data to create compiled coercers into Match under
  `:result. A pre-requisite to use [[coerce!]]."
  [[_ {:keys [parameters coercion]}] opts]
  (if (and parameters coercion)
    (request-coercers coercion parameters opts)))

(defn coerce!
  "Returns a map of coerced input parameters using pre-compiled
  coercers under `:result` (provided by [[compile-request-coercers]].
  Throws `ex-info` if parameters can't be coerced
  If coercion or parameters are not defined, return `nil`"
  [match]
  (if-let [coercers (:result match)]
    (coerce-request coercers match)))
