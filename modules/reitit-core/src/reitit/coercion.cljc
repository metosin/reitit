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
   :path (->ParameterCoercion :path-params :string true true)
   :fragment (->ParameterCoercion :fragment :string true true)})

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

(defn -identity-coercer [value _format]
  value)

;; TODO: support faster key walking, walk/keywordize-keys is quite slow...
(defn request-coercer [coercion type model {::keys [extract-request-format parameter-coercion serialize-failed-result]
                                            :or {extract-request-format extract-request-format-default
                                                 parameter-coercion default-parameter-coercion}}]
  (if coercion
    (if-let [{:keys [keywordize? open? in style]} (parameter-coercion type)]
      (let [transform (comp (if keywordize? walk/keywordize-keys identity) in)
            ->open (if open? #(-open-model coercion %) identity)
            format-schema-pairs (if (= :request style)
                                  (conj (:content model) [:default (:body model)])
                                  [[:default model]])
            format->coercer (some->> (for [[format schema] format-schema-pairs
                                           :when schema
                                           :let [type (case style :request :body style)]]
                                       [format (-request-coercer coercion type (->open schema))])
                                     (filter second)
                                     (seq)
                                     (into {}))]
        (when format->coercer
          (fn [request]
            (let [value (transform request)
                  format (extract-request-format request)
                  coercer (or (format->coercer format)
                              (format->coercer :default)
                              -identity-coercer)
                  result (coercer value format)]
              (if (error? result)
                (request-coercion-failed! result coercion value in request serialize-failed-result)
                result))))))))

(defn extract-response-format-default [request _]
  (-> request :muuntaja/response :format))

(defn response-coercer [coercion {:keys [content body]} {:keys [extract-response-format serialize-failed-result]
                                                         :or {extract-response-format extract-response-format-default}}]
  (if coercion
    (let [per-format-coercers (some->> (for [[format schema] content
                                             :when schema]
                                         [format (-response-coercer coercion schema)])
                                       (filter second)
                                       (seq)
                                       (into {}))
          default (when body (-response-coercer coercion body))]
      (when (or per-format-coercers default)
        (fn [request response]
          (let [format (extract-response-format request response)
                value (:body response)
                coercer (get per-format-coercers format (or default -identity-coercer))
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
  (some->> (for [[k v] parameters, :when v]
             [k (request-coercer coercion k v opts)])
           (filter second) (seq) (into {})))

(defn response-coercers [coercion responses opts]
  (some->> (for [[status model] responses]
             [status (response-coercer coercion model opts)])
           (filter second) (seq) (into {})))

(defn -compile-parameters [data coercion]
  (impl/path-update data [[[:parameters any?] #(-compile-model coercion % nil)]]))

;;
;; api-docs
;;

(defn -warn-unsupported-coercions [{:keys [parameters responses] :as _data}]
  (when (:request parameters)
    (println "WARNING [reitit.coercion]: swagger apidocs don't support :request coercion"))
  (when (some :content (vals responses))
    (println "WARNING [reitit.coercion]: swagger apidocs don't support :responses :content coercion")))

(defn get-apidocs [coercion specification data]
  (let [swagger-parameter {:query :query
                           :body :body
                           :form :formData
                           :header :header
                           :path :path
                           :multipart :formData}]
    (case specification
      :openapi (-get-apidocs coercion specification data)
      :swagger (do
                 (-warn-unsupported-coercions data)
                 (->> (update
                       data
                       :parameters
                       (fn [parameters]
                         (->> parameters
                              (map (fn [[k v]] [(swagger-parameter k) v]))
                              (filter first)
                              (into {}))))
                      (-get-apidocs coercion specification))))))


;;
;; integration
;;

(defn compile-request-coercers
  "A router :compile implementation which reads the `:parameters`
  and `:coercion` data to both compile the schemas and create compiled coercers
  into Match under `:result with the following keys:

   | key       | description
   | ----------|-------------
   | `:data`   | data with compiled schemas
   | `:coerce` | function of `Match -> coerced parameters` to coerce parameters

  A pre-requisite to use [[coerce!]].

  NOTE: this is not needed with ring/http, where the coercion compilation is
  managed in the request coercion middleware/interceptors."
  [[_ {:keys [parameters coercion] :as data}] opts]
  (if (and parameters coercion)
    (let [{:keys [parameters] :as data} (-compile-parameters data coercion)]
      {:data data
       :coerce (request-coercers coercion parameters opts)})))

(defn coerce!
  "Returns a map of coerced input parameters using pre-compiled coercers in `Match`
  under path `[:result :coerce]` (provided by [[compile-request-coercers]].
  Throws `ex-info` if parameters can't be coerced. If coercion or parameters
  are not defined, returns `nil`"
  [match]
  (if-let [coercers (-> match :result :coerce)]
    (coerce-request coercers match)))
