(ns reitit.coercion.spec
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [meta-merge.core :as mm]
            [reitit.coercion :as coercion]
            [reitit.exception :as ex]
            [spec-tools.core :as st #?@(:cljs [:refer [Spec]])]
            [spec-tools.data-spec :as ds #?@(:cljs [:refer [Maybe]])]
            [spec-tools.openapi.core :as openapi]
            [spec-tools.swagger.core :as swagger])
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
  (into-spec [_ _]))

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
    (-get-model-apidocs [_ specification model options]
      (case specification
        :openapi (openapi/transform model (merge opts options))
        (throw
         (ex-info
          (str "Can't produce Spec apidocs for " specification)
          {:type specification, :coercion :spec}))))
    (-get-apidocs [_ specification {:keys [request parameters responses content-types]
                                    :or {content-types ["application/json"]}}]
      (case specification
        :swagger (swagger/swagger-spec
                  (merge
                   (if parameters
                     {::swagger/parameters parameters})
                   (if responses
                     {::swagger/responses
                      (into
                       (empty responses)
                       (for [[k response] responses]
                         [k (as-> response $
                              (dissoc $ :content)
                              (set/rename-keys $ {:body :schema}))]))})))
        ;; :openapi handled in reitit.openapi/-get-apidocs-openapi
        (throw
         (ex-info
          (str "Can't produce Spec apidocs for " specification)
          {:specification specification, :coercion :spec}))))
    (-compile-model [_ model name]
      (into-spec
       (cond
         ;; we are safe!
         (= (count model) 1) (first model)
         ;; here be dragons, best effort
         (every? map? model) (apply mm/meta-merge model)
         ;; all elements are the same
         (apply = model) (first model)
         ;; fail fast
         :else (ex/fail! ::model-error {:message "Can't merge nested clojure specs", :spec model}))
       name))
    (-open-model [_ spec] spec)
    (-encode-error [_ error]
      (let [problems (-> error :problems ::s/problems)]
        (-> error
            (update :spec (comp str s/form))
            (assoc :problems (mapv #(update % :pred stringify-pred) problems)))))
    (-request-coercer [_ type spec]
      (let [{:keys [formats default]} (transformers type)]
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
        (coercion/-request-coercer this :response spec)))
    (-query-string-coercer [this spec]
      nil)))

(def coercion (create default-options))
