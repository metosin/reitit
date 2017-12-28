(ns reitit.ring.spec
  (:require [clojure.spec.alpha :as s]
            [reitit.middleware #?@(:cljs [:refer [Middleware]])]
            [reitit.spec :as rs])
  #?(:clj
     (:import (reitit.middleware Middleware))))

;;
;; Specs
;;

(s/def ::middleware (s/coll-of (partial instance? Middleware)))

(s/def ::data
  (s/keys :req-un [::rs/handler]
          :opt-un [::rs/name ::middleware]))

;;
;; Validator
;;

(defn- validate-ring-route-data [routes spec]
  (->> (for [[p _ c] routes
             [method {:keys [data] :as endpoint}] c
             :when endpoint]
         (when-let [problems (and spec (s/explain-data spec data))]
           (rs/->Problem p method data spec problems)))
       (keep identity) (seq)))

(defn validate-spec!
  [routes {:keys [spec ::rs/explain] :or {explain s/explain-str, spec ::data}}]
  (when-let [problems (validate-ring-route-data routes spec)]
    (rs/throw-on-problems! problems explain)))
