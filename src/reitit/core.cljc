(ns reitit.core
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.impl :as impl #?@(:cljs [:refer [Route]])])
  #?(:clj
     (:import (reitit.impl Route))))

(defprotocol Expand
  (expand [this]))

(extend-protocol Expand

  #?(:clj  clojure.lang.Keyword
     :cljs cljs.core.Keyword)
  (expand [this] {:name this})

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (expand [this] this)

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (expand [this] this)

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (expand [this] {:handler this})

  nil
  (expand [_]))

(defn walk [data {:keys [path meta routes expand]
                  :or {path "", meta [], routes [], expand expand}}]
  (letfn
    [(walk-many [p m r]
       (reduce #(into %1 (walk-one p m %2)) [] r))
     (walk-one [pacc macc routes]
       (if (vector? (first routes))
         (walk-many pacc macc routes)
         (let [[path & [maybe-meta :as args]] routes
               [meta childs] (if (vector? maybe-meta)
                               [{} args]
                               [maybe-meta (rest args)])
               macc (into macc (expand meta))]
           (if (seq childs)
             (walk-many (str pacc path) macc childs)
             [[(str pacc path) macc]]))))]
    (walk-one path meta data)))

(defn map-meta [f routes]
  (mapv #(update % 1 f) routes))

(defn merge-meta [x]
  (reduce
    (fn [acc [k v]]
      (meta-merge acc {k v}))
    {} x))

(defn resolve-routes [data {:keys [coerce] :or {coerce identity} :as opts}]
  (->> (walk data opts)
       (map-meta merge-meta)
       (mapv (partial coerce))
       (filterv identity)))

(defprotocol Routing
  (routes [this])
  (match-by-path [this path])
  (match-by-name [this name] [this name parameters]))

(defrecord Match [template meta path params])

(defrecord LinearRouter [routes data lookup]
  Routing
  (routes [_]
    routes)
  (match-by-path [_ path]
    (reduce
      (fn [acc ^Route route]
        (if-let [params ((:matcher route) path)]
          (reduced (->Match (:path route) (:meta route) path params))))
      nil data))
  (match-by-name [_ name]
    ((lookup name) nil))
  (match-by-name [_ name params]
    ((lookup name) params)))

(defn linear-router [routes]
  (->LinearRouter
    routes
    (mapv (partial apply impl/create) routes)
    (->> (for [[p {:keys [name] :as meta}] routes
               :when name
               :let [route (impl/create p meta)]]
           [name (fn [params]
                   (->Match p meta (impl/path-for route params) params))])
         (into {}))))

(defrecord LookupRouter [routes data lookup]
  Routing
  (routes [_]
    routes)
  (match-by-path [_ path]
    (data path))
  (match-by-name [_ name]
    ((lookup name) nil))
  (match-by-name [_ name params]
    ((lookup name) params)))

(defn lookup-router [routes]
  (when-let [route (some impl/contains-wilds? (map first routes))]
    (throw
      (ex-info
        (str "can't create LookupRouter with wildcard routes: " route)
        {:route route
         :routes routes})))
  (->LookupRouter
    routes
    (->> (for [[p meta] routes]
           [p (->Match p meta p {})])
         (into {}))
    (->> (for [[p {:keys [name] :as meta}] routes
               :when name]
           [name (fn [params]
                   (->Match p meta p params))])
         (into {}))))

(defn router
  ([data]
   (router data {}))
  ([data opts]
   (let [routes (resolve-routes data opts)]
     ((if (some impl/contains-wilds? (map first routes))
        linear-router lookup-router) routes))))
