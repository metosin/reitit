(ns reitit.coercion.malli
  (:require [reitit.coercion :as coercion]
            [malli.transform :as mt]
            [malli.edn :as edn]
            [malli.error :as me]
            [malli.swagger :as swagger]
            [malli.core :as m]
            [clojure.set :as set]))

;;
;; coercion
;;

(defrecord Coercer [decoder encoder validator explainer])

(def string-transformer
  (mt/transformer
    mt/strip-extra-keys-transformer
    mt/string-transformer
    mt/default-value-transformer))

(def json-transformer
  (mt/transformer
    mt/strip-extra-keys-transformer
    mt/json-transformer
    mt/default-value-transformer))

(def default-transformer
  (mt/transformer
    mt/strip-extra-keys-transformer
    mt/default-value-transformer))

;; TODO: are these needed?
(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(defn- -coercer [schema type transformers f opts]
  (if schema
    (let [->coercer (fn [t] (if t (->Coercer (m/decoder schema opts t)
                                             (m/encoder schema opts t)
                                             (m/validator schema opts)
                                             (m/explainer schema opts))))
          {:keys [formats default]} (transformers type)
          default-coercer (->coercer default)
          format-coercers (some->> (for [[f t] formats] [f (->coercer t)]) (filter second) (seq) (into {}))
          get-coercer (cond format-coercers (fn [format] (or (get format-coercers format) default-coercer))
                            default-coercer (constantly default-coercer))]
      (if get-coercer
        (if (= f :decode)
          ;; transform -> validate
          (fn [value format]
            (if-let [coercer (get-coercer format)]
              (let [transform (:decoder coercer)
                    validator (:validator coercer)
                    transformed (transform value)]
                (if (validator transformed)
                  transformed
                  (let [explainer (:explainer coercer)
                        error (explainer transformed)]
                    (coercion/map->CoercionError
                      (assoc error :transformed transformed)))))
              value))
          ;; validate -> transform
          (fn [value format]
            (if-let [coercer (get-coercer format)]
              (let [transform (:encoder coercer)
                    validator (:validator coercer)
                    explainer (:explainer coercer)]
                (if (validator value)
                  (transform value)
                  (coercion/map->CoercionError
                    (explainer value))))
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
  {:coerce-response? coerce-response?
   :transformers {:body {:default default-transformer
                         :formats {"application/json" json-transformer}}
                  :string {:default string-transformer}
                  :response {:default default-transformer
                             :formats {"application/json" json-transformer}}}
   ;; set of keys to include in error messages
   :error-keys #{:type :coercion :in :schema :value :errors :humanized #_:transformed}
   ;; malli options
   :options nil})

(defn create
  ([]
   (create nil))
  ([opts]
   (let [{:keys [transformers coerce-response? options error-keys] :as opts} (merge default-options opts)
         show? (fn [key] (contains? error-keys key))]
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
                                    parameter (extract-parameter in schema)]
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
                                             (update $ :schema swagger/transform {:type :schema})
                                             $))]))}))
           (throw
             (ex-info
               (str "Can't produce Schema apidocs for " specification)
               {:type specification, :coercion :schema}))))
       (-compile-model [_ model _] (m/schema model))
       (-open-model [_ schema] schema)
       (-encode-error [_ error]
         (cond-> error
                 (show? :humanized) (assoc :humanized (me/humanize error {:wrap :message}))
                 (show? :schema) (update :schema edn/write-string opts)
                 (show? :errors) (-> (me/with-error-messages opts)
                                     (update :errors (partial map #(update % :schema edn/write-string opts))))
                 (seq error-keys) (select-keys error-keys)))
       (-request-coercer [_ type schema]
         (-coercer schema type transformers :decode options))
       (-response-coercer [_ schema]
         (if (coerce-response? schema)
           (-coercer schema :response transformers :encode options)))))))

(def coercion (create default-options))
