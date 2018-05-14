(ns reitit.coercion.spec
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as st #?@(:cljs [:refer [Spec]])]
            [spec-tools.data-spec :as ds]
            [spec-tools.transform :as stt]
            [spec-tools.swagger.core :as swagger]
            [reitit.coercion :as coercion]
            [clojure.set :as set])
  #?(:clj
     (:import (spec_tools.core Spec))))

(def string-transformer
  (st/type-transformer
    {:name :string
     :decoders (merge
                 stt/string-type-decoders
                 stt/strip-extra-keys-type-decoders)
     :encoders stt/string-type-encoders
     :default-encoder stt/any->any}))

(def json-transformer
  (st/type-transformer
    {:name :json
     :decoders (merge
                 stt/json-type-decoders
                 stt/strip-extra-keys-type-decoders)
     :encoders stt/json-type-encoders
     :default-encoder stt/any->any}))

(def no-op-transformer
  st/no-op-transformer)

(defprotocol IntoSpec
  (into-spec [this name]))

(defn- ensure-name [?name]
  (or ?name (keyword "" (name (gensym "spec")))))

(extend-protocol IntoSpec

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (into-spec [this name]
    (ds/spec (ensure-name name) this))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (into-spec [this name]
    (ds/spec (ensure-name name) this))

  Spec
  (into-spec [this _] this)

  #?(:clj  Object
     :cljs default)
  (into-spec [this _]
    (st/create-spec {:spec this})))

(defn stringify-pred [pred]
  (str (if (seq? pred) (seq pred) pred)))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(def default-options
  {:coerce-response? coerce-response?
   :transformers {:body {:default no-op-transformer
                         :formats {"application/json" json-transformer}}
                  :string {:default string-transformer}
                  :response {:default no-op-transformer}}})

(defn create [{:keys [transformers coerce-response?] :as opts}]
  ^{:type ::coercion/coercion}
  (reify coercion/Coercion
    (-get-name [_] :spec)
    (-get-options [_] opts)
    (-get-apidocs [this spesification {:keys [parameters responses]}]
      (condp = spesification
        :swagger (swagger/swagger-spec
                   (merge
                     (if parameters
                       {::swagger/parameters
                        (into
                          (empty parameters)
                          (for [[k v] parameters]
                            [k (coercion/-compile-model this v nil)]))})
                     (if responses
                       {::swagger/responses
                        (into
                          (empty responses)
                          (for [[k response] responses
                                :let [response (set/rename-keys response {:body :schema})]]
                            [k (update response :schema #(coercion/-compile-model this % nil))]))})))
        (throw
          (ex-info
            (str "Can't produce Spec apidocs for " spesification)
            {:type spesification, :coercion :spec}))))
    (-compile-model [_ model name]
      (into-spec model name))
    (-open-model [_ spec] spec)
    (-encode-error [_ error]
      (-> error
          (update :spec (comp str s/form))
          (update :problems (partial mapv #(update % :pred stringify-pred)))))
    (-request-coercer [this type spec]
      (let [spec (coercion/-compile-model this spec nil)
            {:keys [formats default]} (transformers type)]
        (fn [value format]
          (if-let [transformer (or (get formats format) default)]
            (let [transformed (st/conform spec value transformer)]
              (if (s/invalid? transformed)
                (let [problems (st/explain-data spec value transformer)]
                  (coercion/map->CoercionError
                    {:spec spec
                     :problems (::s/problems problems)}))
                (s/unform spec transformed)))
            value))))
    (-response-coercer [this spec]
      (if (coerce-response? spec)
        (coercion/-request-coercer this :response spec)))))

(def coercion (create default-options))
