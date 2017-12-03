(ns reitit.ring.coercion.schema
  (:require [schema.core :as s]
            [schema-tools.core :as st]
            [schema.coerce :as sc]
            [schema.utils :as su]
            [schema-tools.coerce :as stc]
            [spec-tools.swagger.core :as swagger]
            [clojure.walk :as walk]
            [reitit.ring.coercion.protocol :as protocol]))

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
        #?@(:clj [(class? x) (.getName ^Class x)])
        (instance? schema.core.OptionalKey x) (pr-str (list 'opt (:k x)))
        (instance? schema.core.RequiredKey x) (pr-str (list 'req (:k x)))
        (and (satisfies? s/Schema x) (record? x)) (try (pr-str (s/explain x)) (catch #?(:clj Exception :cljs js/Error) _ x))
        (instance? schema.utils.ValidationError x) (str (su/validation-error-explain x))
        (instance? schema.utils.NamedError x) (str (su/named-error-explain x))
        :else x))
    schema))

(defrecord SchemaCoercion [name matchers coerce-response?]

  protocol/Coercion
  (get-name [_] name)

  (get-apidocs [_ _ {:keys [parameters responses] :as info}]
    (cond-> (dissoc info :parameters :responses)
            parameters (assoc ::swagger/parameters parameters)
            responses (assoc ::swagger/responses responses)))

  (compile-model [_ model _] model)

  (open-model [_ schema] (st/open-schema schema))

  (encode-error [_ error]
    (-> error
        (update :schema stringify)
        (update :errors stringify)))

  (request-coercer [_ type schema]
    (let [{:keys [formats default]} (matchers type)
          coercers (->> (for [m (conj (vals formats) default)]
                          [m (sc/coercer schema m)])
                        (into {}))]
      (fn [value format]
        (if-let [matcher (or (get formats format) default)]
          (let [coercer (coercers matcher)
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
