(ns reitit.coercion.malli
  (:require [reitit.coercion :as coercion]
            [malli.transform :as mt]
            [malli.swagger :as swagger]
            [malli.core :as m]
            [clojure.set :as set]))

(defrecord Coercer [decoder encoder validator explainer])

(def string-transformer
  mt/string-transformer)

(def json-transformer
  mt/json-transformer)

(def default-transformer
  (mt/transformer {:name :default}))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

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

(def default-options
  {:coerce-response? coerce-response?
   :transformers {:body {:default default-transformer
                         :formats {"application/json" json-transformer}}
                  :string {:default string-transformer}
                  :response {:default default-transformer}}})

(defn create [{:keys [transformers coerce-response?] :as opts}]
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
    (-encode-error [_ error] error)
    (-request-coercer [_ type schema]
      (if schema
        (let [->coercer (fn [t] (->Coercer (m/decoder schema t)
                                           (m/encoder schema t)
                                           (m/validator schema)
                                           (m/explainer schema)))
              {:keys [formats default]} (transformers type)
              default-coercer (->coercer default)
              format-coercers (->> (for [[f t] formats] [f (->coercer t)]) (into {}))
              get-coercer (if (seq format-coercers)
                            (fn [format] (or (get format-coercers format) default-coercer))
                            (constantly default-coercer))]
          (fn [value format]
            (if-let [coercer (get-coercer format)]
              (let [decoder (:decoder coercer)
                    validator (:validator coercer)
                    decoded (decoder value)]
                (if (validator decoded)
                  decoded
                  (let [explainer (:explainer coercer)
                        errors (explainer decoded)]
                    (coercion/map->CoercionError
                      {:schema schema
                       :errors errors}))))
              value)))))
    (-response-coercer [this schema]
      (if (coerce-response? schema)
        (coercion/-request-coercer this :response schema)))))

(def coercion (create default-options))
