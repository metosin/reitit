(ns reitit.ring.coercion.schema
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as st #?@(:cljs [:refer [Spec]])]
            [spec-tools.data-spec :as ds]
            [spec-tools.conform :as conform]
            [spec-tools.swagger.core :as swagger]
            [reitit.ring.coercion.protocol :as protocol])
  #?(:clj
     (:import (spec_tools.core Spec))))

(def string-conforming
  (st/type-conforming
    (merge
      conform/string-type-conforming
      conform/strip-extra-keys-type-conforming)))

(def json-conforming
  (st/type-conforming
    (merge
      conform/json-type-conforming
      conform/strip-extra-keys-type-conforming)))

(def default-conforming
  ::default)

(defprotocol IntoSpec
  (into-spec [this name]))

(extend-protocol IntoSpec

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (into-spec [this name]
    (ds/spec name this))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (into-spec [this name]
    (ds/spec name this))

  Spec
  (into-spec [this _] this)

  #?(:clj  Object
     :cljs default)
  (into-spec [this _]
    (st/create-spec {:spec this})))

;; TODO: proper name!
(def memoized-into-spec
  (memoize #(into-spec %1 (gensym "spec"))))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(defrecord SpecCoercion [name conforming coerce-response?]

  protocol/Coercion
  (get-name [_] name)

  (compile [_ model _]
    (memoized-into-spec model))

  (get-apidocs [_ _ {:keys [parameters responses] :as info}]
    (cond-> (dissoc info :parameters :responses)
            parameters (assoc
                         ::swagger/parameters
                         (into
                           (empty parameters)
                           (for [[k v] parameters]
                             [k memoized-into-spec])))
            responses (assoc
                        ::swagger/responses
                        (into
                          (empty responses)
                          (for [[k response] responses]
                            [k (update response :schema memoized-into-spec)])))))

  (make-open [_ spec] spec)

  (encode-error [_ error]
    (update error :spec (comp str s/form)))

  (request-coercer [_ type spec]
    (let [spec (memoized-into-spec spec)
          {:keys [formats default]} (conforming type)]
      (fn [value format]
        (if-let [conforming (or (get formats format) default)]
          (let [conformed (st/conform spec value conforming)]
            (if (s/invalid? conformed)
              (let [problems (st/explain-data spec value conforming)]
                (protocol/map->CoercionError
                  {:spec spec
                   :problems (::s/problems problems)}))
              (s/unform spec conformed)))
          value))))

  (response-coercer [this spec]
    (if (coerce-response? spec)
      (protocol/request-coercer this :response spec))))

(def default-options
  {:coerce-response? coerce-response?
   :conforming {:body {:default default-conforming
                       :formats {"application/json" json-conforming}}
                :string {:default string-conforming}
                :response {:default default-conforming}}})

(defn create [{:keys [conforming coerce-response?]}]
  (->SpecCoercion :spec conforming coerce-response?))

(def coercion (create default-options))
