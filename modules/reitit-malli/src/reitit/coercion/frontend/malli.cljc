(ns reitit.coercion.frontend.malli
  "Optimized coercion implementation use with
  Reitit-frontend.

  Only supports string coercion, OpenAPI and Swagger support
  removed."
  (:require [clojure.walk :as walk]
            [malli.core :as m]
            [malli.experimental.lite :as l]
            [malli.transform :as mt]
            [malli.util :as mu]
            [reitit.coercion :as coercion]
            [reitit.coercion.malli.protocols :as p]))

(defn- -provider [transformer]
  (reify p/TransformationProvider
    (-transformer [_ {:keys [strip-extra-keys default-values]}]
      (mt/transformer
       (if strip-extra-keys (mt/strip-extra-keys-transformer))
       transformer
       (if default-values (mt/default-value-transformer))))))

(def string-transformer-provider (-provider (mt/string-transformer)))
(def default-transformer-provider (-provider nil))

(defn- -coercer [schema type transformers {:keys [validate enabled options]}]
  (if schema
    (let [->coercer (fn [t]
                      (let [decoder (if t (m/decoder schema options t) identity)
                            encoder (if t (m/encoder schema options t) identity)
                            validator (if validate (m/validator schema options) (constantly true))
                            explainer (m/explainer schema options)]
                        (reify p/Coercer
                          (-decode [_ value] (decoder value))
                          (-encode [_ value] (encoder value))
                          (-validate [_ value] (validator value))
                          (-explain [_ value] (explainer value)))))
          {:keys [default]} (transformers type)
          default-coercer (->coercer default)
          coercer default-coercer]
      (if (and enabled coercer)
        (fn [value format]
          (if coercer
            (let [transformed (p/-decode coercer value)]
              (if (p/-validate coercer transformed)
                transformed
                (let [error (p/-explain coercer transformed)]
                  (coercion/map->CoercionError
                    (assoc error :transformed transformed)))))
            value))))))

(defn- -query-string-coercer
  "Create coercer for query-parameters, always allows extra params and does
  encoding using string-transformer."
  [schema string-transformer-provider options]
  (let [;; Always allow extra paramaters on query-parameters encoding
        open-schema (mu/open-schema schema)
        ;; Do not remove extra keys
        string-transformer (if (satisfies? p/TransformationProvider string-transformer-provider)
                             (p/-transformer string-transformer-provider (assoc options :strip-extra-keys false))
                             string-transformer-provider)
        encoder (m/encoder open-schema options string-transformer)]
    (fn [value format]
      (if encoder
        (encoder value)
        value))))

;;
;; public api
;;

;; TODO: this is much too compöex
(def default-options
  {:transformers {:body {:default default-transformer-provider}
                  :string {:default string-transformer-provider}}
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
   (let [{:keys [transformers lite compile options encode-error] :as opts} (merge default-options opts)
         ;; Query-string-coercer needs to construct transfomer without strip-extra-keys so it will
         ;; use the transformer-provider directly.
         string-transformer-provider (:default (:string transformers))
         transformers (walk/prewalk #(if (satisfies? p/TransformationProvider %) (p/-transformer % opts) %) transformers)
         compile (if lite (fn [schema options]
                            (compile (binding [l/*options* options] (l/schema schema)) options))
                          compile)]
     ^{:type ::coercion/coercion}
     (reify coercion/Coercion
       (-get-name [_] :malli-frontend)
       (-get-options [_] opts)
       (-get-model-apidocs [this specification model options]
         nil)
       (-get-apidocs [this specification {:keys [parameters responses] :as data}]
         nil)
       (-compile-model [_ model _]
         (if (= 1 (count model))
           (compile (first model) options)
           (reduce (fn [x y] (mu/merge x y options)) (map #(compile % options) model))))
       (-open-model [_ schema] schema)
       (-encode-error [_ error]
         ;; NOTE: Is this needed for FE?
         nil)
       (-request-coercer [_ type schema]
         (-coercer schema type transformers opts))
       (-response-coercer [_ schema]
         nil)
       (-query-string-coercer [_ schema]
         (-query-string-coercer schema string-transformer-provider opts))))))

(def coercion (create default-options))
