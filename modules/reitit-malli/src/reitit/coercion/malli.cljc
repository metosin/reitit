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
;; swagger
;;

(defmulti extract-parameter (fn [in _ _] in))

(defmethod extract-parameter :body [_ schema options]
  (let [swagger-schema (swagger/transform schema (merge options {:in :body, :type :parameter}))]
    [{:in "body"
      :name (:title swagger-schema "body")
      :description (:description swagger-schema "")
      :required (not= :maybe (m/type schema))
      :schema swagger-schema}]))

(defmethod extract-parameter :default [in schema options]
  (let [{:keys [properties required]} (swagger/transform schema (merge options {:in in, :type :parameter}))]
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
   ;; strip-extra-keys (effects only predefined transformers)
   :strip-extra-keys true
   ;; add/set default values
   :default-values true
   ;; encode-error
   :encode-error nil
   ;; malli options
   :options nil})

;; TODO: this is now seems like a generic transforming function that could be used in all of malli, spec, schema
;; ... just tranform the schemas in place
;; also, this has internally massive amount of duplicate code, could be simplified
;; ... tests too
(defn -get-apidocs-openapi
  [coercion {:keys [request parameters responses content-types] :or {content-types ["application/json"]}} options]
  (let [{:keys [body multipart]} parameters
        parameters (dissoc parameters :request :body :multipart)
        ->content (fn [data schema]
                    (merge
                     {:schema schema}
                     (select-keys data [:description :examples])
                     (:openapi data)))
        ->schema-object #(coercion/-get-model-apidocs coercion :openapi %1 (merge options %2))]
    (merge
     (when (seq parameters)
       {:parameters
        (->> (for [[in schema] parameters
                   :let [{:keys [properties required]} (->schema-object schema {:in in :type :parameter})
                         required? (partial contains? (set required))]
                   [k schema] properties]
               (merge {:in (name in)
                       :name k
                       :required (required? k)
                       :schema schema}
                      (select-keys schema [:description])))
             (into []))})
     (when body
       ;; body uses a single schema to describe every :requestBody
       ;; the schema-object transformer should be able to transform into distinct content-types
       {:requestBody {:content (into {}
                                     (map (fn [content-type]
                                            (let [schema (->schema-object body {:in :requestBody
                                                                                :type :schema
                                                                                :content-type content-type})]
                                              [content-type {:schema schema}])))
                                     content-types)}})

     (when request
       ;; request allow to different :requestBody per content-type
       {:requestBody
        {:content (merge
                   (select-keys request [:description])
                   (when-let [{:keys [schema] :as data} (coercion/get-default request)]
                     (into {}
                           (map (fn [content-type]
                                  (let [schema (->schema-object schema {:in :requestBody
                                                                        :type :schema
                                                                        :content-type content-type})]
                                    [content-type (->content data schema)])))
                           content-types))
                   (into {}
                         (map (fn [[content-type {:keys [schema] :as data}]]
                                (let [schema (->schema-object schema {:in :requestBody
                                                                      :type :schema
                                                                      :content-type content-type})]
                                  [content-type (->content data schema)])))
                         (:content request)))}})
     (when multipart
       {:requestBody
        {:content
         {"multipart/form-data"
          {:schema
           (->schema-object multipart {:in :requestBody
                                       :type :schema
                                       :content-type "multipart/form-data"})}}}})
     (when responses
       {:responses
        (into {}
              (map (fn [[status {:keys [content], :as response}]]
                     (let [default (coercion/get-default-schema response)
                           content (-> (merge
                                        (when default
                                          (into {}
                                                (map (fn [content-type]
                                                       (let [schema (->schema-object default {:in :responses
                                                                                              :type :schema
                                                                                              :content-type content-type})]
                                                         [content-type (->content nil schema)])))
                                                content-types))
                                        (when content
                                          (into {}
                                                (map (fn [[content-type {:keys [schema] :as data}]]
                                                       (let [schema (->schema-object schema {:in :responses
                                                                                             :type :schema
                                                                                             :content-type content-type})]
                                                         [content-type (->content data schema)])))
                                                content)))
                                       (dissoc :default))]
                       [status (merge (select-keys response [:description])
                                      (when content
                                        {:content content}))]))
                   responses))}))))

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
           :openapi (json-schema/transform model options)
           (throw
            (ex-info
             (str "Can't produce Malli apidocs for " specification)
             {:type specification, :coercion :malli}))))
       (-get-apidocs [this specification {:keys [parameters responses] :as data}]
         (case specification
           :swagger (merge
                     (if parameters
                       {:parameters
                        (->> (for [[in schema] parameters
                                   parameter (extract-parameter in schema options)]
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
           :openapi (-get-apidocs-openapi this data options)
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
