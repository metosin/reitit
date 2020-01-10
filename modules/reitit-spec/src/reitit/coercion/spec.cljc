(ns reitit.coercion.spec
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as st #?@(:cljs [:refer [Spec]])]
            [spec-tools.data-spec :as ds #?@(:cljs [:refer [Maybe]])]
            [spec-tools.swagger.core :as swagger]
            [reitit.coercion :as coercion]
            [clojure.set :as set])
  #?(:clj
     (:import (spec_tools.core Spec)
              (spec_tools.data_spec Maybe))))

(def string-transformer
  (st/type-transformer
    st/strip-extra-keys-transformer
    st/string-transformer))

(def json-transformer
  (st/type-transformer
    st/strip-extra-keys-transformer
    st/json-transformer))

(def strip-extra-keys-transformer
  st/strip-extra-keys-transformer)

(def no-op-transformer
  (reify
    st/Transformer
    (-name [_] ::no-op)
    (-encoder [_ _ _])
    (-decoder [_ _ _])))

(defprotocol IntoSpec
  (into-spec [this name]))

(defn- ensure-name [?name]
  (or ?name (keyword "spec" (name (gensym "")))))

(extend-protocol IntoSpec

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (into-spec [this name]
    (dissoc (ds/spec (ensure-name name) this) :name))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (into-spec [this name]
    (dissoc (ds/spec (ensure-name name) this) :name))

  #?(:clj  clojure.lang.PersistentVector
     :cljs cljs.core.PersistentVector)
  (into-spec [this name]
    (dissoc (ds/spec (ensure-name name) this) :name))

  Maybe
  (into-spec [this name]
    (ds/spec (ensure-name name) this))

  Spec
  (into-spec [this _] this)

  #?(:clj  Object
     :cljs default)
  (into-spec [this _]
    (st/create-spec {:spec this}))

  nil
  (into-spec [this _]))

(defn stringify-pred [pred]
  (str (if (seq? pred) (seq pred) pred)))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(def default-options
  {:coerce-response? coerce-response?
   :transformers {:body {:default strip-extra-keys-transformer
                         :formats {"application/json" json-transformer}}
                  :string {:default string-transformer}
                  :response {:default no-op-transformer}}})

(defn create [{:keys [transformers coerce-response?] :as opts}]
  ^{:type ::coercion/coercion}
  (reify coercion/Coercion
    (-get-name [_] :spec)
    (-get-options [_] opts)
    (-get-apidocs [this specification {:keys [parameters responses]}]
      (case specification
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
                          (for [[k response] responses]
                            [k (as-> response $
                                     (set/rename-keys $ {:body :schema})
                                     (if (:schema $)
                                       (update $ :schema #(coercion/-compile-model this % nil))
                                       $))]))})))
        (throw
          (ex-info
            (str "Can't produce Spec apidocs for " specification)
            {:specification specification, :coercion :spec}))))
    (-compile-model [_ model name]
      (into-spec model name))
    (-open-model [_ spec] spec)
    (-encode-error [_ error]
      (let [problems (-> error :problems ::s/problems)]
        (-> error
            (update :spec (comp str s/form))
            (assoc :problems (mapv #(update % :pred stringify-pred) problems)))))
    (-request-coercer [this type spec]
      (let [spec (coercion/-compile-model this spec nil)
            {:keys [formats default]} (transformers type)]
        (fn [value format]
          (if-let [transformer (or (get formats format) default)]
            (let [coerced (st/coerce spec value transformer)]
              (if (s/valid? spec coerced)
                coerced
                (let [transformed (st/conform spec coerced transformer)]
                  (if (s/invalid? transformed)
                    (let [problems (st/explain-data spec coerced transformer)]
                      (coercion/map->CoercionError
                        {:spec spec
                         :problems problems}))
                    (s/unform spec transformed)))))
            value))))
    (-response-coercer [this spec]
      (if (coerce-response? spec)
        (coercion/-request-coercer this :response spec)))))

(def coercion (create default-options))
