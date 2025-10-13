(ns reitit.coercion
  (:require [#?(:clj reitit.walk :cljs clojure.walk) :as walk]
            [reitit.core :as r]
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
  ;; TODO doc options:
  (-get-model-apidocs [this specification model options] "Convert model into a format that can be used in api docs")
  (-compile-model [this model name] "Compiles a model")
  (-open-model [this model] "Returns a new model which allows extra keys in maps")
  (-encode-error [this error] "Converts error in to a serializable format")
  (-request-coercer [this type model] "Returns a `value format => value` request coercion function")
  (-response-coercer [this model] "Returns a `value format => value` response coercion function")
  (-query-string-coercer [this model] "Returns a `value => value` query string coercion function"))

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
(defn request-coercer [coercion type model {::keys [extract-request-format parameter-coercion serialize-failed-result skip]
                                            :or {extract-request-format extract-request-format-default
                                                 parameter-coercion default-parameter-coercion
                                                 skip #{}}}]
  (if coercion
    (when-let [{:keys [keywordize? open? in style]} (parameter-coercion type)]
      (when-not (skip style)
        (let [transform (comp (if keywordize? walk/keywordize-keys identity) in)
              ->open (if open? #(-open-model coercion %) identity)
              coercer (-request-coercer coercion style (->open model))]
          (when coercer
            (fn [request]
              (let [value (transform request)
                    format (extract-request-format request)
                    result (coercer value format)]
                (if (error? result)
                  (request-coercion-failed! result coercion value in request serialize-failed-result)
                  result)))))))))

(defn get-default [request-or-response]
  (or (-> request-or-response :content :default)
      (some->> request-or-response :body (assoc {} :schema))))

(defn content-request-coercer [coercion {:keys [content body]} {::keys [extract-request-format serialize-failed-result]
                                                                :or {extract-request-format extract-request-format-default}}]
  (when coercion
    (let [in :body-params
          format->coercer (some->> (concat (when body
                                             [[:default (-request-coercer coercion :body body)]])
                                           (for [[format {:keys [schema]}] content, :when schema]
                                             [format (-request-coercer coercion :body schema)]))
                                   (filter second) (seq) (into (array-map)))]
      (when format->coercer
        (fn [request]
          (let [value (in request)
                format (extract-request-format request)
                coercer (or (format->coercer format)
                            (format->coercer :default)
                            -identity-coercer)
                result (coercer value format)]
            (if (error? result)
              (request-coercion-failed! result coercion value in request serialize-failed-result)
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

(defn request-coercers
  ([coercion parameters opts]
   (some->> (for [[k v] parameters, :when v]
              [k (request-coercer coercion k v opts)])
            (filter second) (seq) (into {})))
  ([coercion parameters route-request opts]
   (let [crc (when route-request (some->> (content-request-coercer coercion route-request opts) (array-map :request)))
         rcs (request-coercers coercion parameters (cond-> opts route-request (assoc ::skip #{:body})))]
     (if (and crc rcs) (into crc (vec rcs)) (or crc rcs)))))

(defn extract-response-format-default [request _]
  (-> request :muuntaja/response :format))

(defn -format->coercer [coercion {:keys [content body]} _opts]
  (->> (concat (when body
                 [[:default (-response-coercer coercion body)]])
               (for [[format {:keys [schema]}] content, :when schema]
                 [format (-response-coercer coercion schema)]))
       (filter second) (into (array-map))))

(defn response-coercer [coercion responses {:keys [extract-response-format serialize-failed-result]
                                            :or {extract-response-format extract-response-format-default}
                                            :as opts}]
  (when coercion
    (let [status->format->coercer
          (into {}
                (for [[status model] responses]
                  (do
                    (when-not (or (= :default status) (int? status))
                      (throw (ex-info "Response status must be int or :default" {:status status})))
                    [status (-format->coercer coercion model opts)])))]
      (when-not (every? empty? (vals status->format->coercer)) ;; fast path: return nil if there are no models to coerce
        (fn [request response]
          (let [format->coercer (or (status->format->coercer (:status response))
                                    (status->format->coercer :default))
                format (extract-response-format request response)
                coercer (when format->coercer
                          (or (format->coercer format)
                              (format->coercer :default)))]
            (if-not coercer
              response
              (let [value (:body response)
                    coerced (coercer (:body response) format)
                    result (if (error? coerced)
                             (response-coercion-failed! coerced coercion value request response serialize-failed-result)
                             coerced)]
                (impl/fast-assoc response :body result)))))))))

(defn -compile-parameters [data coercion]
  (impl/path-update data [[[:parameters any?] #(-compile-model coercion % nil)]]))

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

(defn coerce-query-params
  "Uses an input schema and coercion implementation from the given match to
  encode query-parameters map.

  If no match, no input schema or coercion implementation, just returns the
  original parameters map."
  [match query-params]
  (when query-params
    (let [coercion (-> match :data :coercion)
          schema (when coercion
                   (-compile-model coercion (-> match :data :parameters :query) nil))
          coercer (when (and schema coercion)
                    (-query-string-coercer coercion schema))]
      (if coercer
        (let [result (coercer query-params :default)]
          (if (error? result)
            (throw (ex-info (str "Query parameters coercion failed")
                            result))
            result))
        query-params))))

(defn match->path
  "Create routing path from given match and optional query-parameters map.

  Query-parameters are encoded using the input schema and coercion implementation."
  ([match]
   (r/match->path match))
  ([match query-params]
   (r/match->path match (coerce-query-params match query-params))))
