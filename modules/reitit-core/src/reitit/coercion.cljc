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
   :form (->ParameterCoercion :form-params :string true true)
   :header (->ParameterCoercion :headers :string true true)
   :path (->ParameterCoercion :path-params :string true true)})

;; TODO: Fold this into `request-coercion-failed-join-errors!` in 1.0.0 when
;;       switching to the new response format.
(defn ^:no-doc request-coercion-failed-fail-fast! [result coercion value in request]
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

(defn ^:no-doc merge-coercion-errors
  "Coalesces a list of coercion errors on the form `{:in <>}`"
  [errors]
  (->> errors
       (map (fn [{in :in :as data}]
              {in (merge (into {} (:error data)) (dissoc data :in :error))}))
       (apply merge)))

(defn ^:no-doc request-coercion-failed-join-errors!
  [errors request]
  (let [result (merge-coercion-errors errors)]
    (throw
     (ex-info
      (str "Request coercion failed: " (pr-str result))
      (assoc result
             :type ::request-coercion
             :request request)))))

;; TODO: Fold this into `response-coercion-failed-join-errors!` in 1.0.0 when
;;       switching to the new response format.
(defn ^:no-doc response-coercion-failed-fail-fast! [result coercion value request response]
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

(defn ^:no-doc response-coercion-failed-join-errors!
  [errors request response]
  (let [result (merge-coercion-errors errors)]
    (throw
     (ex-info
      (str "Response coercion failed: " (pr-str result))
      (assoc result
             :type ::response-coercion
             :request request
             :response response)))))

(defn extract-request-format-default [request]
  (-> request :muuntaja/request :format))

;; TODO: support faster key walking, walk/keywordize-keys is quite slow...
(defn request-coercer [coercion type model {::keys [extract-request-format
                                                    parameter-coercion
                                                    coerce-all-on-error?]
                                            :or {extract-request-format extract-request-format-default
                                                 parameter-coercion default-parameter-coercion
                                                 coerce-all-on-error? false}}]
  (if coercion
    (if-let [{:keys [keywordize? open? in style]} (parameter-coercion type)]
      (let [transform (comp (if keywordize? walk/keywordize-keys identity) in)
            model (if open? (-open-model coercion model) model)
            coercer (-request-coercer coercion style model)]
        (fn [request]
          (let [value (transform request)
                format (extract-request-format request)
                result (coercer value format)]
            (if (error? result)
              (if coerce-all-on-error?
                [nil {:error result
                      :in (if (= in :body-params) :body in)
                      :coercion coercion
                      :value value}]
                (request-coercion-failed-fail-fast! result coercion value in request))
              [result])))))))

(defn extract-response-format-default [request _]
  (-> request :muuntaja/response :format))

(defn response-coercer [coercion body {::keys [extract-response-format coerce-all-on-error?]
                                       :or {extract-response-format extract-response-format-default
                                            coerce-all-on-error? false}}]
  (if coercion
    (if-let [coercer (-response-coercer coercion body)]
      (fn [request response]
        (let [format (extract-response-format request response)
              value (:body response)
              result (coercer value format)]
          (if (error? result)
            (if coerce-all-on-error?
              [nil {:error result
                    :in :body
                    :coercion coercion
                    :value value}]
              (response-coercion-failed-fail-fast! result coercion value request response))
            [result]))))))

(defn encode-single-error [data]
  (-encode-error (:coercion data) (update data :coercion -get-name)))

(defn encode-error [data]
  (if (not-any? #(contains? data %)
                [:body :form-params :query-params :headers :path-params])
    (encode-single-error (dissoc data :request :response))
    (let [errors (->> (dissoc data :request :response :type)
                      (map (fn [[k v]] [k (encode-single-error v)])))]
      (into (select-keys data [:type]) errors))))

(defn coerce-request [coercers request]
  (let [[result errors] (reduce-kv
                         (fn [[acc errors] k coercer]
                           (let [[result error] (coercer request)]
                             (if error
                               [acc (conj errors error)]
                               [(impl/fast-assoc acc k result) errors])))
                         [{}]
                         coercers)]
    (when errors
      (request-coercion-failed-join-errors! errors request))
    result))

(defn coerce-response [coercers request response]
  (if response
    (if-let [coercer (or (coercers (:status response)) (coercers :default))]
      (let [[result error] (coercer request response)]
        (if error
          (response-coercion-failed-join-errors! [error] request response)
          (impl/fast-assoc response :body result))))))

(defn request-coercers [coercion parameters opts]
  (->> (for [[k v] parameters
             :when v]
         [k (request-coercer coercion k v opts)])
       (filter second)
       (into {})))

(defn response-coercers [coercion responses opts]
  (->> (for [[status {:keys [body]}] responses :when body]
         [status (response-coercer coercion body opts)])
       (filter second)
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
