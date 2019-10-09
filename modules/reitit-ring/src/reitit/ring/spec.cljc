(ns reitit.ring.spec
  (:require [clojure.spec.alpha :as s]
            [reitit.middleware :as middleware]
            [reitit.spec :as rs]
            [reitit.exception :as exception]))

;;
;; Specs
;;

(s/def ::middleware (s/coll-of #(satisfies? middleware/IntoMiddleware %) :kind vector?))
(s/def ::get map?)
(s/def ::head map?)
(s/def ::post map?)
(s/def ::put map?)
(s/def ::delete map?)
(s/def ::connect map?)
(s/def ::options map?)
(s/def ::trace map?)
(s/def ::patch map?)


(s/def ::data
  (s/keys :opt-un [::rs/handler ::rs/name ::rs/no-doc ::middleware]))

;;
;; Validator
;;

(defn merge-specs [specs]
  (when-let [non-specs (seq (remove #(or (s/spec? %) (s/get-spec %)) specs))]
    (exception/fail!
      ::invalid-specs
      {:specs specs
       :invalid non-specs}))
  (s/merge-spec-impl (vec specs) (vec specs) nil))

(defn validate-route-data [routes key wrap spec]
  (->> (for [[p _ c] routes
             [method {:keys [data] :as endpoint}] c
             :when endpoint
             :let [target (key endpoint)
                   component-specs (seq (keep :spec target))
                   specs (keep identity (into [spec] component-specs))
                   spec (wrap (merge-specs specs))]]
         (when-let [problems (and spec (s/explain-data spec data))]
           (rs/->Problem p method data spec problems)))
       (keep identity) (seq)))

(defn validate
  [routes {:keys [spec ::rs/wrap] :or {spec ::data, wrap identity}}]
  (when-let [problems (validate-route-data routes :middleware wrap spec)]
    (exception/fail!
      ::rs/invalid-route-data
      {:problems problems})))
