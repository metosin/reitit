(ns reitit.http.spec
  (:require [clojure.spec.alpha :as s]
            [reitit.ring.spec :as rrs]
            [reitit.interceptor :as interceptor]
            [reitit.exception :as exception]
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

(defn validate
  [routes {:keys [spec] :or {spec ::data}}]
  (when-let [problems (rrs/validate-route-data routes :interceptors spec)]
    (exception/fail!
      ::invalid-route-data
      {:problems problems})))
