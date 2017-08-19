(ns reitit.core
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.impl :as impl #?@(:cljs [:refer [Route]])])
  #?(:clj
     (:import (reitit.impl Route))))

(defprotocol Expand
  (expand [this opts]))

(extend-protocol Expand

  #?(:clj  clojure.lang.Keyword
     :cljs cljs.core.Keyword)
  (expand [this _] {:name this})

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (expand [this _] this)

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (expand [this _] this)

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (expand [this _] {:handler this})

  nil
  (expand [_ _]))

(defn walk [data {:keys [path meta routes expand]
                  :or {path "", meta [], routes [], expand expand}
                  :as opts}]
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
               macc (into macc (expand meta opts))]
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

(defn resolve-routes [data {:keys [coerce] :as opts}]
  (cond->> (->> (walk data opts) (map-meta merge-meta))
           coerce (into [] (keep #(coerce % opts)))))

(defn name-lookup [[_ {:keys [name]}] opts]
  (if name #{name}))

(defn find-names [routes opts]
  (into [] (keep #(-> % second :name) routes)))

(defn compile-route [[p m :as route] {:keys [compile] :as opts}]
  [p m (if compile (compile route opts))])

(defprotocol Routing
  (routes [this])
  (route-names [this])
  (match-by-path [this path])
  (match-by-name [this name] [this name params]))

(defrecord Match [template meta handler params path])
(defrecord PartialMatch [template meta handler params required])

(defn partial-match? [x]
  (instance? PartialMatch x))

(defn match-by-name!
  ([this name]
   (match-by-name! this name nil))
  ([this name params]
   (if-let [match (match-by-name this name params)]
     (if-not (partial-match? match)
       match
       (impl/throw-on-missing-path-params
         (:template match) (:required match) params)))))

(def default-router-options
  {:lookup name-lookup
   :expand expand
   :coerce (fn [route _] route)
   :compile (fn [[_ {:keys [handler]}] _] handler)})

(defrecord LinearRouter [routes names data lookup]
  Routing
  (routes [_]
    routes)
  (route-names [_]
    names)
  (match-by-path [_ path]
    (reduce
      (fn [acc ^Route route]
        (if-let [params ((:matcher route) path)]
          (reduced (->Match (:path route) (:meta route) (:handler route) params path))))
      nil data))
  (match-by-name [_ name]
    (if-let [match (lookup name)]
      (match nil)))
  (match-by-name [_ name params]
    (if-let [match (lookup name)]
      (match params))))

(defn linear-router
  "Creates a [[LinearRouter]] from resolved routes and optional
  expanded options. See [[router]] for available options"
  ([routes]
   (linear-router routes {}))
  ([routes opts]
   (let [compiled (map #(compile-route % opts) routes)
         names (find-names routes opts)
         [data lookup] (reduce
                         (fn [[data lookup] [p {:keys [name] :as meta} handler]]
                           (let [{:keys [params] :as route} (impl/create [p meta handler])
                                 f #(if-let [path (impl/path-for route %)]
                                      (->Match p meta handler % path)
                                      (->PartialMatch p meta handler % params))]
                             [(conj data route)
                              (if name (assoc lookup name f) lookup)]))
                         [[] {}] compiled)]
     (->LinearRouter routes names data lookup))))

(defrecord LookupRouter [routes names data lookup]
  Routing
  (routes [_]
    routes)
  (route-names [_]
    names)
  (match-by-path [_ path]
    (data path))
  (match-by-name [_ name]
    (if-let [match (lookup name)]
      (match nil)))
  (match-by-name [_ name params]
    (if-let [match (lookup name)]
      (match params))))

(defn lookup-router
  "Creates a [[LookupRouter]] from resolved routes and optional
  expanded options. See [[router]] for available options"
  ([routes]
   (lookup-router routes {}))
  ([routes opts]
   (when-let [route (some impl/contains-wilds? (map first routes))]
     (throw
       (ex-info
         (str "can't create LookupRouter with wildcard routes: " route)
         {:route route
          :routes routes})))
   (let [compiled (map #(compile-route % opts) routes)
         names (find-names routes opts)
         [data lookup] (reduce
                         (fn [[data lookup] [p {:keys [name] :as meta} handler]]
                           [(assoc data p (->Match p meta handler {} p))
                            (if name
                              (assoc lookup name #(->Match p meta handler % p))
                              lookup)]) [{} {}] compiled)]
     (->LookupRouter routes names data lookup))))

(defn router
  "Create a [[Router]] from raw route data and optionally an options map.
  If routes contain wildcards, a [[LinearRouter]] is used, otherwise a
  [[LookupRouter]]. The following options are available:

  | key        | description |
  | -----------|-------------|
  | `:path`    | Base-path for routes (default `\"\"`)
  | `:routes`  | Initial resolved routes (default `[]`)
  | `:meta`    | Initial expanded route-meta vector (default `[]`)
  | `:expand`  | Function of `arg opts => meta` to expand route arg to route meta-data (default `reitit.core/expand`)
  | `:coerce`  | Function of `route opts => route` to coerce resolved route, can throw or return `nil`
  | `:compile` | Function of `route opts => handler` to compile a route handler"
  ([data]
   (router data {}))
  ([data opts]
   (let [opts (meta-merge default-router-options opts)
         routes (resolve-routes data opts)]
     ((if (some impl/contains-wilds? (map first routes))
        linear-router lookup-router) routes opts))))
