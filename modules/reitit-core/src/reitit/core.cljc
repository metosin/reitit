(ns reitit.core
  (:require [meta-merge.core :refer [meta-merge]]
            [clojure.string :as str]
            [reitit.trie :as trie]
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
                  :or {meta [], routes [], expand expand}
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
    (walk-one path (mapv identity meta) data)))

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

;; This whole function might be more efficient and easier to understand with transducers.
(defn conflicting-routes [routes]
  (some->>
    (loop [[r & rest] routes, acc {}]
      (if (seq rest)
        (let [conflicting (set (keep #(if (impl/conflicting-routes? r %) %) rest))]
          (recur rest (update acc r (fnil (comp set concat) #{}) conflicting)))
        acc))
    (filter (comp seq second))
    (seq)
    (into {})))

(defn conflicts-str [conflicts]
  (apply str "Router contains conflicting routes:\n\n"
         (mapv
           (fn [[[path] vals]]
             (str "   " path "\n-> " (str/join "\n-> " (mapv first vals)) "\n\n"))
           conflicts)))

(defn throw-on-conflicts! [conflicts]
  (throw
    (ex-info
      (conflicts-str conflicts)
      {:conflicts conflicts})))

(defn name-lookup [[_ {:keys [name]}] opts]
  (if name #{name}))

(defn find-names [routes opts]
  (into [] (keep #(-> % second :name)) routes))

(defn- compile-route [[p m :as route] {:keys [compile] :as opts}]
  [p m (if compile (compile route opts))])

(defn- compile-routes [routes opts]
  (into [] (keep #(compile-route % opts) routes)))

(defn route-info [route]
  (select-keys (impl/create route) [:path :parts :params :result :meta]))

(defprotocol Router
  (router-name [this])
  (routes [this])
  (options [this])
  (route-names [this])
  (match-by-path [this path])
  (match-by-name [this name] [this name params]))

(defn router? [x]
  (satisfies? Router x))

(defrecord Match [template meta result params path])
(defrecord PartialMatch [template meta result params required])

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
   :compile (fn [[_ {:keys [handler]}] _] handler)
   :conflicts throw-on-conflicts!})

(defn linear-router
  "Creates a linear-router from resolved routes and optional
  expanded options. See [[router]] for available options"
  ([routes]
   (linear-router routes {}))
  ([routes opts]
   (let [compiled (compile-routes routes opts)
         names (find-names routes opts)
         [data lookup] (reduce
                         (fn [[data lookup] [p {:keys [name] :as meta} result]]
                           (let [{:keys [params] :as route} (impl/create [p meta result])
                                 f #(if-let [path (impl/path-for route %)]
                                      (->Match p meta result % path)
                                      (->PartialMatch p meta result % params))]
                             [(conj data route)
                              (if name (assoc lookup name f) lookup)]))
                         [[] {}] compiled)
         lookup (impl/fast-map lookup)]
     (reify
       Router
       (router-name [_]
         :linear-router)
       (routes [_]
         compiled)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (reduce
           (fn [acc ^Route route]
             (if-let [params ((:matcher route) path)]
               (reduced (->Match (:path route) (:meta route) (:result route) params path))))
           nil data))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name params]
         (if-let [match (impl/fast-get lookup name)]
           (match params)))))))

(defn lookup-router
  "Creates a lookup-router from resolved routes and optional
  expanded options. See [[router]] for available options"
  ([routes]
   (lookup-router routes {}))
  ([routes opts]
   (when-let [wilds (seq (filter impl/wild-route? routes))]
     (throw
       (ex-info
         (str "can't create :lookup-router with wildcard routes: " wilds)
         {:wilds wilds
          :routes routes})))
   (let [compiled (compile-routes routes opts)
         names (find-names routes opts)
         [data lookup] (reduce
                         (fn [[data lookup] [p {:keys [name] :as meta} result]]
                           [(assoc data p (->Match p meta result {} p))
                            (if name
                              (assoc lookup name #(->Match p meta result % p))
                              lookup)]) [{} {}] compiled)
         data (impl/fast-map data)
         lookup (impl/fast-map lookup)]
     (reify Router
       (router-name [_]
         :lookup-router)
       (routes [_]
         compiled)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (impl/fast-get data path))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name params]
         (if-let [match (impl/fast-get lookup name)]
           (match params)))))))

(defn prefix-tree-router
  "Creates a prefix-tree router from resolved routes and optional
  expanded options. See [[router]] for available options"
  ([routes]
   (prefix-tree-router routes {}))
  ([routes opts]
   (let [compiled (compile-routes routes opts)
         names (find-names routes opts)
         [node lookup] (reduce
                         (fn [[node lookup] [p {:keys [name] :as meta} result]]
                           (let [{:keys [params] :as route} (impl/create [p meta result])
                                 f #(if-let [path (impl/path-for route %)]
                                      (->Match p meta result % path)
                                      (->PartialMatch p meta result % params))]
                             [(trie/insert node p (->Match p meta result nil nil))
                              (if name (assoc lookup name f) lookup)]))
                         [nil {}] compiled)
         lookup (impl/fast-map lookup)]
     (reify
       Router
       (router-name [_]
         :prefix-tree-router)
       (routes [_]
         compiled)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (if-let [match (trie/lookup node path {})]
           (-> (:data match)
               (assoc :params (:params match))
               (assoc :path path))))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name params]
         (if-let [match (impl/fast-get lookup name)]
           (match params)))))))

(defn single-static-path-router
  "Creates a fast router of 1 static route(s) and optional
  expanded options. See [[router]] for available options"
  ([routes]
   (single-static-path-router routes {}))
  ([routes opts]
   (when (or (not= (count routes) 1) (some impl/wild-route? routes))
     (throw
       (ex-info
         (str ":single-static-path-router requires exactly 1 static route: " routes)
         {:routes routes})))
   (let [[n :as names] (find-names routes opts)
         [[p meta result] :as compiled] (compile-routes routes opts)
         p #?(:clj (.intern ^String p) :cljs p)
         match (->Match p meta result {} p)]
     (reify Router
       (router-name [_]
         :single-static-path-router)
       (routes [_]
         compiled)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (if (#?(:clj .equals :cljs =) p path)
           match))
       (match-by-name [_ name]
         (if (= n name)
           match))
       (match-by-name [_ name params]
         (if (= n name)
           (impl/fast-assoc match :params params)))))))

(defn mixed-router
  "Creates two routers: [[lookup-router]] or [[single-static-path-router]] for
  static routes and [[prefix-tree-router]] for wildcard routes. All
  routes should be non-conflicting. Takes resolved routes and optional
  expanded options. See [[router]] for options."
  ([routes]
   (mixed-router routes {}))
  ([routes opts]
   (let [{wild true, lookup false} (group-by impl/wild-route? routes)
         compiled (compile-routes routes opts)
         ->static-router (if (= 1 (count lookup)) single-static-path-router lookup-router)
         wildcard-router (prefix-tree-router wild opts)
         static-router (->static-router lookup opts)
         names (find-names routes opts)]
     (reify Router
       (router-name [_]
         :mixed-router)
       (routes [_]
         compiled)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (or (match-by-path static-router path)
             (match-by-path wildcard-router path)))
       (match-by-name [_ name]
         (or (match-by-name static-router name)
             (match-by-name wildcard-router name)))
       (match-by-name [_ name params]
         (or (match-by-name static-router name params)
             (match-by-name wildcard-router name params)))))))

(defn router
  "Create a [[Router]] from raw route data and optionally an options map.
  Selects implementation based on route details. The following options
  are available:

  | key          | description |
  | -------------|-------------|
  | `:path`      | Base-path for routes
  | `:routes`    | Initial resolved routes (default `[]`)
  | `:meta`      | Initial route meta (default `{}`)
  | `:expand`    | Function of `arg opts => meta` to expand route arg to route meta-data (default `reitit.core/expand`)
  | `:coerce`    | Function of `route opts => route` to coerce resolved route, can throw or return `nil`
  | `:compile`   | Function of `route opts => result` to compile a route handler
  | `:conflicts` | Function of `{route #{route}} => side-effect` to handle conflicting routes (default `reitit.core/throw-on-conflicts!`)
  | `:router`    | Function of `routes opts => router` to override the actual router implementation"
  ([data]
   (router data {}))
  ([data opts]
   (let [{:keys [router] :as opts} (meta-merge default-router-options opts)
         routes (resolve-routes data opts)
         conflicting (conflicting-routes routes)
         wilds? (boolean (some impl/wild-route? routes))
         all-wilds? (every? impl/wild-route? routes)
         router (cond
                  router router
                  (and (= 1 (count routes)) (not wilds?)) single-static-path-router
                  conflicting linear-router
                  (not wilds?) lookup-router
                  all-wilds? prefix-tree-router
                  :else mixed-router)]

     (when-let [conflicts (:conflicts opts)]
       (when conflicting (conflicts conflicting)))

     (router routes opts))))
