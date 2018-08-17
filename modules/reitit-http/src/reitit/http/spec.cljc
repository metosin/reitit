(ns reitit.http.spec
  (:require [clojure.spec.alpha :as s]
            [reitit.ring.spec :as rrs]
            [reitit.interceptor :as interceptor]
            [reitit.spec :as rs]))

;;
;; Specs
;;

(s/def ::interceptors (s/coll-of (partial satisfies? interceptor/IntoInterceptor)))

(s/def ::data
  (s/keys :opt-un [::rs/handler ::rs/name ::interceptors]))

;;
;; Validator
;;

(defn validate-spec!
  [routes {:keys [spec ::rs/explain] :or {explain s/explain-str, spec ::data}}]
  (when-let [problems (rrs/validate-route-data routes :interceptors spec)]
    (rs/throw-on-problems! problems explain)))
