(ns reitit.coercion.malli
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [malli.core :as m]
            [malli.edn :as edn]
            [malli.error :as me]
            [malli.experimental.lite :as l]
            [malli.json-schema :as json-schema]
            [malli.swagger :as swagger]
            [malli.transform :as mt]
            [malli.util :as mu]
            [reitit.coercion :as coercion]))

;;
;; coercion
;;

(defprotocol Coercer
  (-decode [this value])
  (-encode [this value])
  (-validate [this value])
  (-explain [this value]))

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

(defn- -coercer [schema type transformers f {:keys [validate enabled options]}]
  (if schema
    (let [->coercer (fn [t]
                      (let [decoder (if t (m/decoder schema options t) identity)
                            encoder (if t (m/encoder schema options t) identity)
                            validator (if validate (m/validator schema options) (constantly true))
                            explainer (m/explainer schema options)]
                        (reify Coercer
                          (-decode [_ value] (decoder value))
                          (-encode [_ value] (encoder value))
                          (-validate [_ value] (validator value))
                          (-explain [_ value] (explainer value)))))
          {:keys [formats default]} (transformers type)
          default-coercer (->coercer default)
          format-coercers (some->> (for [[f t] formats] [f (->coercer t)]) (filter second) (seq) (into {}))
          get-coercer (cond format-coercers (fn [format] (or (get format-coercers format) default-coercer))
                            default-coercer (constantly default-coercer))]
      (if (and enabled get-coercer)
        (if (= f :decode)
          ;; decode: decode -> validate
          (fn [value format]
            (if-let [coercer (get-coercer format)]
              (let [transformed (-decode coercer value)]
                (if (-validate coercer transformed)
                  transformed
                  (let [error (-explain coercer transformed)]
                    (coercion/map->CoercionError
                     (assoc error :transformed transformed)))))
              value))
          ;; encode: decode -> validate -> encode
          (fn [value format]
            (let [transformed (-decode default-coercer value)]
              (if-let [coercer (get-coercer format)]
                (if (-validate coercer transformed)
                  (-encode coercer transformed)
                  (let [error (-explain coercer transformed)]
                    (coercion/map->CoercionError
                     (assoc error :transformed transformed))))
                value))))))))

;;
;; public api
;;

;; TODO: this is much too compöex
(def default-options
  {:transformers {:body {:default default-transformer-provider
                         :formats {"application/json" json-transformer-provider}}
                  :string {:default string-transformer-provider}
                  :response {:default default-transformer-provider
                             :formats {"application/json" json-transformer-provider}}}
   ;; set of keys to include in error messages
   :error-keys #{:type :coercion :in #_:schema :value #_:errors :humanized #_:transformed}
   ;; support lite syntax?
   :lite true
   ;; schema identity function (default: close all map schemas)
   :compile mu/closed-schema
   ;; validate request & response
   :validate true
   ;; top-level short-circuit to disable request & response coercion
   :enabled true
   ;; strip-extra-keys (affects only predefined transformers)
   :strip-extra-keys true
   ;; add/set default values
   :default-values true
   ;; encode-error
   :encode-error nil
   ;; malli options
   :options nil})

(defn create
  ([]
   (create nil))
  ([opts]
   (let [{:keys [transformers lite compile options error-keys encode-error] :as opts} (merge default-options opts)
         show? (fn [key] (contains? error-keys key))
         transformers (walk/prewalk #(if (satisfies? TransformationProvider %) (-transformer % opts) %) transformers)
         compile (if lite (fn [schema options]
                            (compile (binding [l/*options* options] (l/schema schema)) options))
                          compile)]
     ^{:type ::coercion/coercion}
     (reify coercion/Coercion
       (-get-name [_] :malli)
       (-get-options [_] opts)
       (-get-model-apidocs [this specification model options]
         (case specification
           :openapi (if (= :parameter (:type options))
                      ;; For :parameters we need to output an object schema with actual :properties.
                      ;; The caller will iterate through the properties and add them individually to the openapi doc.
                      ;; Thus, we deref to get the actual [:map ..] instead of some ref-schema.
                      (let [should-be-map (m/deref model)]
                        (when-not (= :map (m/type should-be-map))
                          (println "WARNING: Unsupported schema for OpenAPI (expected :map schema)" (select-keys options [:in :parameter]) should-be-map))
                        (json-schema/transform should-be-map (merge opts options)))
                      (json-schema/transform model (merge opts options)))
           (throw
            (ex-info
             (str "Can't produce Malli apidocs for " specification)
             {:type specification, :coercion :malli}))))
       (-get-apidocs [this specification {:keys [parameters responses] :as data}]
         (case specification
           :swagger (swagger/swagger-spec
                      (merge
                        (if parameters
                          {::swagger/parameters
                           (into
                             (empty parameters)
                             (for [[k v] parameters]
                               [k (compile v options)]))})
                        (if responses
                          {::swagger/responses
                           (into
                             (empty responses)
                             (for [[k response] responses]
                               [k (as-> response $
                                        (set/rename-keys $ {:body :schema})
                                        (if (:schema $)
                                          (update $ :schema compile options)
                                          $))]))})))
           ;; :openapi handled in reitit.openapi/-get-apidocs-openapi
           (throw
            (ex-info
             (str "Can't produce Malli apidocs for " specification)
             {:type specification, :coercion :malli}))))
       (-compile-model [_ model _]
         (if (= 1 (count model))
           (compile (first model) options)
           (reduce (fn [x y] (mu/merge x y options)) (map #(compile % options) model))))
       (-open-model [_ schema] schema)
       (-encode-error [_ error]
         (cond-> error
           (show? :humanized) (assoc :humanized (me/humanize error {:wrap :message}))
           (show? :schema) (update :schema edn/write-string opts)
           (show? :errors) (-> (me/with-error-messages opts)
                               (update :errors (partial map #(update % :schema edn/write-string opts))))
           (seq error-keys) (select-keys error-keys)
           encode-error (encode-error)))
       (-request-coercer [_ type schema]
         (-coercer schema type transformers :decode opts))
       (-response-coercer [_ schema]
         (-coercer schema :response transformers :encode opts))))))

(def coercion (create default-options))
