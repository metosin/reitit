(ns reitit.coercion.malli
  (:require [reitit.coercion :as coercion]
            [malli.transform :as mt]
            [malli.edn :as edn]
            [malli.error :as me]
            [malli.util :as mu]
            [malli.swagger :as swagger]
            [malli.core :as m]
            [clojure.set :as set]
            [clojure.walk :as walk]))

;;
;; coercion
;;

(defrecord Coercer [decoder encoder validator explainer])

(defprotocol TransformationProvider
  (-transformer [this options]))

(defn- -provider [transformer]
  (reify TransformationProvider
    (-transformer [_ {:keys [strip-extra-keys default-values]}]
      (mt/transformer
        (if strip-extra-keys (mt/strip-extra-keys-transformer))
        transformer
        (if default-values (mt/default-value-transformer))))))

(def string-transformer-provider (-provider (mt/string-transformer)))
(def json-transformer-provider (-provider (mt/json-transformer)))
(def default-transformer-provider (-provider nil))

(defn- -coercer [schema type transformers f encoder opts]
  (if schema
    (let [->coercer (fn [t] (if t (->Coercer (m/decoder schema opts t)
                                             (m/encoder schema opts t)
                                             (m/validator schema opts)
                                             (m/explainer schema opts))))
          {:keys [formats default]} (transformers type)
          default-coercer (->coercer default)
          encode (or encoder (fn [value _format] value))
          format-coercers (some->> (for [[f t] formats] [f (->coercer t)]) (filter second) (seq) (into {}))
          get-coercer (cond format-coercers (fn [format] (or (get format-coercers format) default-coercer))
                            default-coercer (constantly default-coercer))]
      (if get-coercer
        (if (= f :decode)
          ;; decode -> validate
          (fn [value format]
            (if-let [coercer (get-coercer format)]
              (let [decoder (:decoder coercer)
                    validator (:validator coercer)
                    transformed (decoder value)]
                (if (validator transformed)
                  transformed
                  (let [explainer (:explainer coercer)
                        error (explainer transformed)]
                    (coercion/map->CoercionError
                      (assoc error :transformed transformed)))))
              value))
          ;; decode -> validate -> encode
          (fn [value format]
            (if-let [coercer (get-coercer format)]
              (let [decoder (:decoder coercer)
                    validator (:validator coercer)
                    transformed (decoder value)]
                (if (validator transformed)
                  (encode transformed format)
                  (let [explainer (:explainer coercer)
                        error (explainer transformed)]
                    (coercion/map->CoercionError
                      (assoc error :transformed transformed)))))
              value)))))))

;;
;; swagger
;;

(defmulti extract-parameter (fn [in _] in))

(defmethod extract-parameter :body [_ schema]
  (let [swagger-schema (swagger/transform schema {:in :body, :type :parameter})]
    [{:in "body"
      :name (:title swagger-schema "")
      :description (:description swagger-schema "")
      :required (not= :maybe (m/name schema))
      :schema swagger-schema}]))

(defmethod extract-parameter :default [in schema]
  (let [{:keys [properties required]} (swagger/transform schema {:in in, :type :parameter})]
    (mapv
      (fn [[k {:keys [type] :as schema}]]
        (merge
          {:in (name in)
           :name k
           :description (:description schema "")
           :type type
           :required (contains? (set required) k)}
          schema))
      properties)))

;;
;; public api
;;

(def default-options
  {:transformers {:body {:default default-transformer-provider
                         :formats {"application/json" json-transformer-provider}}
                  :string {:default string-transformer-provider}
                  :response {:default default-transformer-provider}}
   ;; set of keys to include in error messages
   :error-keys #{:type :coercion :in :schema :value :errors :humanized #_:transformed}
   ;; schema identity function
   :compile mu/closed-schema
   ;; strip-extra-keys (effects only default transformers!)
   :strip-extra-keys true
   ;; add default values
   :default-values true
   ;; malli options
   :options nil})

(defn create
  ([]
   (create nil))
  ([opts]
   (let [{:keys [transformers compile options error-keys] :as opts} (merge default-options opts)
         show? (fn [key] (contains? error-keys key))
         transformers (walk/prewalk #(if (satisfies? TransformationProvider %) (-transformer % opts) %) transformers)]
     ^{:type ::coercion/coercion}
     (reify coercion/Coercion
       (-get-name [_] :malli)
       (-get-options [_] opts)
       (-get-apidocs [_ specification {:keys [parameters responses]}]
         (case specification
           :swagger (merge
                      (if parameters
                        {:parameters
                         (->> (for [[in schema] parameters
                                    parameter (extract-parameter in (compile schema))]
                                parameter)
                              (into []))})
                      (if responses
                        {:responses
                         (into
                           (empty responses)
                           (for [[status response] responses]
                             [status (as-> response $
                                           (set/rename-keys $ {:body :schema})
                                           (update $ :description (fnil identity ""))
                                           (if (:schema $)
                                             (-> $
                                                 (update :schema compile)
                                                 (update :schema swagger/transform {:type :schema}))
                                             $))]))}))
           (throw
             (ex-info
               (str "Can't produce Schema apidocs for " specification)
               {:type specification, :coercion :schema}))))
       (-compile-model [_ model _] (compile model))
       (-open-model [_ schema] schema)
       (-encode-error [_ error]
         (cond-> error
                 (show? :humanized) (assoc :humanized (me/humanize error {:wrap :message}))
                 (show? :schema) (update :schema edn/write-string opts)
                 (show? :errors) (-> (me/with-error-messages opts)
                                     (update :errors (partial map #(update % :schema edn/write-string opts))))
                 (seq error-keys) (select-keys error-keys)))
       (-request-coercer [_ type schema]
         (-coercer (compile schema) type transformers :decode nil options))
       (-response-coercer [_ schema]
         (let [schema (compile schema)
               encoder (-coercer schema :body transformers :encode nil options)]
           (-coercer schema :response transformers :encode encoder options)))))))

(def coercion (create default-options))
