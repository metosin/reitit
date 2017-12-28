(ns reitit.ring.spec
  (:require [clojure.spec.alpha :as s]
            [reitit.middleware :as middleware]
            [reitit.spec :as rs]))

;;
;; Specs
;;

(s/def ::middleware (s/coll-of (partial satisfies? middleware/IntoMiddleware)))

(s/def ::data
  (s/keys :req-un [::rs/handler]
          :opt-un [::rs/name ::middleware]))

;;
;; Validator
;;

(defn merge-specs [specs]
  (when-let [non-specs (seq (remove #(or (s/spec? %) (s/get-spec %)) specs))]
    (throw
      (ex-info
        (str "Not all specs satisfy the Spec protocol: " non-specs)
        {:specs specs
         :non-specs non-specs})))
  (s/merge-spec-impl (vec specs) (vec specs) nil))

(defn- validate-ring-route-data [routes spec]
  (->> (for [[p _ c] routes
             [method {:keys [data middleware] :as endpoint}] c
             :when endpoint
             :let [mw-specs (seq (keep :spec middleware))
                   specs (keep identity (into [spec] mw-specs))
                   spec (merge-specs specs)]]
         (when-let [problems (and spec (s/explain-data spec data))]
           (rs/->Problem p method data spec problems)))
       (keep identity) (seq)))

(defn validate-spec!
  [routes {:keys [spec ::rs/explain] :or {explain s/explain-str, spec ::data}}]
  (when-let [problems (validate-ring-route-data routes spec)]
    (rs/throw-on-problems! problems explain)))
