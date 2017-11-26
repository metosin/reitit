(ns reitit.ring.coercion.schema
  (:require [schema.core :as s]
            [schema-tools.core :as st]
            [schema.coerce :as sc]
            [schema.utils :as su]
            [schema-tools.coerce :as stc]
            [spec-tools.swagger.core :as swagger]
            [clojure.walk :as walk]
            [reitit.ring.coercion.protocol :as protocol])
  (:import (schema.core OptionalKey RequiredKey)
           (schema.utils ValidationError NamedError)))

(def string-coercion-matcher
  stc/string-coercion-matcher)

(def json-coercion-matcher
  stc/json-coercion-matcher)

(def default-coercion-matcher
  (constantly nil))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(defn stringify [schema]
  (walk/prewalk
    (fn [x]
      (cond
        (class? x) (.getName ^Class x)
        (instance? OptionalKey x) (pr-str (list 'opt (:k x)))
        (instance? RequiredKey x) (pr-str (list 'req (:k x)))
        (and (satisfies? s/Schema x) (record? x)) (try (pr-str (s/explain x)) (catch Exception _ x))
        (instance? ValidationError x) (str (su/validation-error-explain x))
        (instance? NamedError x) (str (su/named-error-explain x))
        :else x))
    schema))

(defrecord SchemaCoercion [name matchers coerce-response?]

  protocol/Coercion
  (get-name [_] name)

  (compile [_ model _]
    model)

  (get-apidocs [_ _ {:keys [parameters responses] :as info}]
    (cond-> (dissoc info :parameters :responses)
            parameters (assoc
                         ::swagger/parameters
                         parameters)
            responses (assoc
                        ::swagger/responses
                        responses)))

  (make-open [_ schema] (st/open-schema schema))

  (encode-error [_ error]
    (-> error
        (update :schema stringify)
        (update :errors stringify)))

  ;; TODO: create all possible coercers ahead of time
  (request-coercer [_ type schema]
    (let [{:keys [formats default]} (matchers type)]
      (fn [value format]
        (if-let [matcher (or (get formats format) default)]
          (let [coercer (sc/coercer schema matcher)
                coerced (coercer value)]
            (if-let [error (su/error-val coerced)]
              (protocol/map->CoercionError
                {:schema schema
                 :errors error})
              coerced))
          value))))

  (response-coercer [this schema]
    (if (coerce-response? schema)
      (protocol/request-coercer this :response schema))))

(def default-options
  {:coerce-response? coerce-response?
   :matchers {:body {:default default-coercion-matcher
                     :formats {"application/json" json-coercion-matcher}}
              :string {:default string-coercion-matcher}
              :response {:default default-coercion-matcher}}})

(defn create [{:keys [matchers coerce-response?]}]
  (->SchemaCoercion :schema matchers coerce-response?))

(def coercion (create default-options))
