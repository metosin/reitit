(ns reitit.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [reitit.core :as reitit]))

;;
;; routes
;;

(s/def ::path (s/with-gen (s/and string? #(str/starts-with? % "/"))
                          #(gen/fmap (fn [s] (str "/" s)) (s/gen string?))))

(s/def ::arg (s/and any? (complement vector?)))
(s/def ::meta (s/map-of keyword? any?))
(s/def ::result any?)

(s/def ::raw-route
  (s/cat :path ::path
         :arg (s/? ::arg)
         :childs (s/* (s/spec (s/and ::raw-route)))))

(s/def ::raw-routes
  (s/or :route ::raw-route
        :routes (s/coll-of ::raw-route :into [])))

(s/def ::route
  (s/cat :path ::path
         :meta ::meta))

(s/def ::routes
  (s/or :route ::route
        :routes (s/coll-of ::route :into [])))

;;
;; router
;;

(s/def ::router reitit/router?)

(s/def :reitit.router/path (s/or :empty #{""} :path ::path))

(s/def :reitit.router/routes ::routes)

(s/def :reitit.router/meta ::meta)

(s/def :reitit.router/expand
  (s/fspec :args (s/cat :arg ::arg, :opts ::opts)
           :ret ::route))

(s/def :reitit.router/coerce
  (s/fspec :args (s/cat :route (s/spec ::route), :opts ::opts)
           :ret ::route))

(s/def :reitit.router/compile
  (s/fspec :args (s/cat :route (s/spec ::route), :opts ::opts)
           :ret ::result))

(s/def :reitit.router/conflicts
  (s/fspec :args (s/cat :conflicts (s/map-of ::route (s/coll-of ::route :into #{})))))

(s/def :reitit.router/router
  (s/fspec :args (s/cat :routes ::routes, :opts ::opts)
           :ret ::router))

;; TODO: fspecs fail..
(s/def ::opts
  (s/nilable
    (s/keys :opt-un [:reitit.router/path
                     :reitit.router/routes
                     :reitit.router/meta
                     #_:reitit.router/expand
                     #_:reitit.router/coerce
                     #_:reitit.router/compile
                     #_:reitit.router/conflicts
                     #_:reitit.router/router])))

(s/fdef reitit/router
        :args (s/or :1arity (s/cat :data (s/spec ::raw-routes))
                    :2arity (s/cat :data (s/spec ::raw-routes), :opts ::opts))
        :ret ::router)
