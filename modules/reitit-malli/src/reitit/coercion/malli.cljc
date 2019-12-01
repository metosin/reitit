(ns reitit.coercion.malli
  (:require [reitit.coercion :as coercion]
            [malli.transform :as mt]
            [malli.core :as m]))

(defrecord Coercer [decoder encoder validator explainer])

(def string-transformer
  mt/string-transformer)

(def json-transformer
  mt/json-transformer)

(def default-transformer
  (mt/transformer {:name :default}))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

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
    (-get-apidocs [this specification {:keys [parameters responses]}]
      ;; TODO: this looks identical to spec, refactor when schema is done.
      #_(case specification
          :swagger (swagger/swagger-spec
                     (merge
                       (if parameters
                         {:swagger/parameters
                          (into
                            (empty parameters)
                            (for [[k v] parameters]
                              [k (coercion/-compile-model this v nil)]))})
                       (if responses
                         {:swagger/responses
                          (into
                            (empty responses)
                            (for [[k response] responses]
                              [k (as-> response $
                                       (set/rename-keys $ {:body :schema})
                                       (if (:schema $)
                                         (update $ :schema #(coercion/-compile-model this % nil))
                                         $))]))})))
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
