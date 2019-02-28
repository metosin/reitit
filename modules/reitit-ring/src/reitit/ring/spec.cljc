(ns reitit.ring.spec
  (:require [clojure.spec.alpha :as s]
            [reitit.middleware :as middleware]
            [reitit.spec :as rs]
            [reitit.exception :as exception]))

;;
;; Specs
;;

(s/def ::middleware (s/coll-of #(satisfies? middleware/IntoMiddleware %)))

(s/def ::data
  (s/keys :req-un [::rs/handler]
          :opt-un [::rs/name ::middleware]))

;;
;; Validator
;;

(defn merge-specs [specs]
  (when-let [non-specs (seq (remove #(or (s/spec? %) (s/get-spec %)) specs))]
    (exception/fail!
      (str "Not all specs satisfy the Spec protocol: " non-specs)
      {:specs specs
       :non-specs non-specs}))
  (s/merge-spec-impl (vec specs) (vec specs) nil))

(defn validate-route-data [routes key spec]
  (->> (for [[p _ c] routes
             [method {:keys [data] :as endpoint}] c
             :when endpoint
             :let [target (key endpoint)
                   mw-specs (seq (keep :spec target))
                   specs (keep identity (into [spec] mw-specs))
                   spec (merge-specs specs)]]
         (when-let [problems (and spec (s/explain-data spec data))]
           (rs/->Problem p method data spec problems)))
       (keep identity) (seq)))

(defn validate-spec!
  [routes {:keys [spec ::rs/explain] :or {explain s/explain-str, spec ::data}}]
  (when-let [problems (validate-route-data routes :middleware spec)]
    (rs/throw-on-problems! problems explain)))
