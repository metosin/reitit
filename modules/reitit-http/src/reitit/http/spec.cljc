(ns reitit.http.spec
  (:require [clojure.spec.alpha :as s]
            [reitit.exception :as exception]
            [reitit.interceptor :as interceptor]
            [reitit.ring.spec :as rrs]
            [reitit.spec :as rs]))

;;
;; Specs
;;

(s/def ::interceptors (s/coll-of (partial satisfies? interceptor/IntoInterceptor)))

(s/def ::data
  (s/keys :opt-un [::rs/handler ::rs/name ::rs/no-doc ::interceptors]))

;;
;; Validator
;;

(defn validate
  [routes {:keys [spec ::rs/wrap] :or {spec ::data, wrap identity}}]
  (when-let [problems (rrs/validate-route-data routes :interceptors wrap spec)]
    (exception/fail!
     ::rs/invalid-route-data
     {:problems problems})))
