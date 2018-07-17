(ns reitit.core
  (:require [meta-merge.core :refer [meta-merge]]
            [clojure.string :as str]
            [reitit.segment :as segment]
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

(defn walk [raw-routes {:keys [path data routes expand]
                        :or {data [], routes [], expand expand}
                        :as opts}]
  (letfn
    [(walk-many [p m r]
       (reduce #(into %1 (walk-one p m %2)) [] r))
     (walk-one [pacc macc routes]
       (if (vector? (first routes))
         (walk-many pacc macc routes)
         (when (string? (first routes))
           (let [[path & [maybe-arg :as args]] routes
                 [data childs] (if (or (vector? maybe-arg) (nil? maybe-arg))
                                 [{} args]
                                 [maybe-arg (rest args)])
                 macc (into macc (expand data opts))
                 child-routes (walk-many (str pacc path) macc (keep identity childs))]
             (if (seq childs) (seq child-routes) [[(str pacc path) macc]])))))]
    (walk-one path (mapv identity data) raw-routes)))

(defn map-data [f routes]
  (mapv #(update % 1 f) routes))

(defn merge-data [x]
  (reduce
    (fn [acc [k v]]
      (meta-merge acc {k v}))
    {} x))

(defn resolve-routes [raw-routes {:keys [coerce] :as opts}]
  (cond->> (->> (walk raw-routes opts) (map-data merge-data))
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

(defn name-lookup [[_ {:keys [name]}] _]
  (if name #{name}))

(defn find-names [routes _]
  (into [] (keep #(-> % second :name)) routes))

(defn- compile-route [[p m :as route] {:keys [compile] :as opts}]
  [p m (if compile (compile route opts))])

(defn- compile-routes [routes opts]
  (into [] (keep #(compile-route % opts) routes)))

(defn route-info [route]
  (select-keys (impl/create route) [:path :path-parts :path-params :result :data]))

(defprotocol Router
  (router-name [this])
  (routes [this])
  (options [this])
  (route-names [this])
  (match-by-path [this path])
  (match-by-name [this name] [this name path-params]))

(defn router? [x]
  (satisfies? Router x))

(defrecord Match [template data result path-params path])
(defrecord PartialMatch [template data result path-params required])

(defn partial-match? [x]
  (instance? PartialMatch x))

(defn match-by-name!
  ([this name]
   (match-by-name! this name nil))
  ([this name path-params]
   (if-let [match (match-by-name this name path-params)]
     (if-not (partial-match? match)
       match
       (impl/throw-on-missing-path-params
         (:template match) (:required match) path-params)))))

(defn match->path
  ([match]
   (match->path match nil))
  ([match query-params]
   (some-> match :path (cond-> query-params (str "?" (impl/query-string query-params))))))

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
   (let [names (find-names routes opts)
         [pl nl] (reduce
                   (fn [[pl nl] [p {:keys [name] :as data} result]]
                     (let [{:keys [path-params] :as route} (impl/create [p data result])
                           f #(if-let [path (impl/path-for route %)]
                                (->Match p data result % path)
                                (->PartialMatch p data result % path-params))]
                       [(conj pl route)
                        (if name (assoc nl name f) nl)]))
                   [[] {}] routes)
         lookup (impl/fast-map nl)]
     ^{:type ::router}
     (reify
       Router
       (router-name [_]
         :linear-router)
       (routes [_]
         routes)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ raw-path]
         (let [{:keys [path query-string fragment-string] :as path-data}
               (impl/pre-process-path raw-path)]
           (when-some [match (reduce
                              (fn [_ ^Route route]
                                (if-let [path-params ((:matcher route) path)]
                                  (reduced (->Match (:path route) (:data route) (:result route) path-params path))))
                              nil pl)]
             (merge match
                    path-data
                    {:path raw-path}))))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name path-params]
         (if-let [match (impl/fast-get lookup name)]
           (match (impl/path-params path-params))))))))

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
   (let [names (find-names routes opts)
         [pl nl] (reduce
                   (fn [[pl nl] [p {:keys [name] :as data} result]]
                     [(assoc pl p (->Match p data result {} p))
                      (if name
                        (assoc nl name #(->Match p data result % p))
                        nl)]) [{} {}] routes)
         data (impl/fast-map pl)
         lookup (impl/fast-map nl)]
     ^{:type ::router}
     (reify Router
       (router-name [_]
         :lookup-router)
       (routes [_]
         routes)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ raw-path]
         (let [{:keys [path query-string fragment-string] :as path-data}
               (impl/pre-process-path raw-path)]
           (when-some [match (impl/fast-get data path)]
             (merge match
                    path-data
                    {:path raw-path}))))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name path-params]
         (if-let [match (impl/fast-get lookup name)]
           (match (impl/path-params path-params))))))))

(defn segment-router
  "Creates a special prefix-tree style segment router from resolved routes and optional
  expanded options. See [[router]] for available options"
  ([routes]
   (segment-router routes {}))
  ([routes opts]
   (let [names (find-names routes opts)
         [pl nl] (reduce
                   (fn [[pl nl] [p {:keys [name] :as data} result]]
                     (let [{:keys [path-params] :as route} (impl/create [p data result])
                           f #(if-let [path (impl/path-for route %)]
                                (->Match p data result % path)
                                (->PartialMatch p data result % path-params))]
                       [(segment/insert pl p (->Match p data result nil nil))
                        (if name (assoc nl name f) nl)]))
                   [nil {}] routes)
         lookup (impl/fast-map nl)]
     ^{:type ::router}
     (reify
       Router
       (router-name [_]
         :segment-router)
       (routes [_]
         routes)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ raw-path]
         (let [{:keys [path query-string fragment-string] :as path-data}
               (impl/pre-process-path raw-path)]
           (when-some [match (segment/lookup pl path)]
             (-> (:data match)
                 (merge path-data)
                 (assoc :path-params (:path-params match)
                        :path raw-path)))))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name path-params]
         (if-let [match (impl/fast-get lookup name)]
           (match (impl/path-params path-params))))))))

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
         [[p data result] :as compiled] routes
         p #?(:clj (.intern ^String p) :cljs p)
         match (->Match p data result {} p)]
     ^{:type ::router}
     (reify Router
       (router-name [_]
         :single-static-path-router)
       (routes [_]
         routes)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ raw-path]
         (let [{:keys [path query-string fragment-string] :as path-data}
               (impl/pre-process-path raw-path)]
           (when-some [match (if (#?(:clj .equals :cljs =) p path)
                               match)]
             (merge match
                    path-data
                    {:path raw-path}))))
       (match-by-name [_ name]
         (if (= n name)
           match))
       (match-by-name [_ name path-params]
         (if (= n name)
           (impl/fast-assoc match :path-params (impl/path-params path-params))))))))

(defn mixed-router
  "Creates two routers: [[lookup-router]] or [[single-static-path-router]] for
  static routes and [[segment-router]] for wildcard routes. All
  routes should be non-conflicting. Takes resolved routes and optional
  expanded options. See [[router]] for options."
  ([routes]
   (mixed-router routes {}))
  ([routes opts]
   (let [{wild true, lookup false} (group-by impl/wild-route? routes)
         ->static-router (if (= 1 (count lookup)) single-static-path-router lookup-router)
         wildcard-router (segment-router wild opts)
         static-router (->static-router lookup opts)
         names (find-names routes opts)]
     ^{:type ::router}
     (reify Router
       (router-name [_]
         :mixed-router)
       (routes [_]
         routes)
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
       (match-by-name [_ name path-params]
         (or (match-by-name static-router name path-params)
             (match-by-name wildcard-router name path-params)))))))

(defn router
  "Create a [[Router]] from raw route data and optionally an options map.
  Selects implementation based on route details. The following options
  are available:

  | key          | description |
  | -------------|-------------|
  | `:path`      | Base-path for routes
  | `:routes`    | Initial resolved routes (default `[]`)
  | `:data`      | Initial route data (default `{}`)
  | `:spec`      | clojure.spec definition for a route data, see `reitit.spec` on how to use this
  | `:expand`    | Function of `arg opts => data` to expand route arg to route data (default `reitit.core/expand`)
  | `:coerce`    | Function of `route opts => route` to coerce resolved route, can throw or return `nil`
  | `:compile`   | Function of `route opts => result` to compile a route handler
  | `:validate`  | Function of `routes opts => ()` to validate route (data) via side-effects
  | `:conflicts` | Function of `{route #{route}} => ()` to handle conflicting routes (default `reitit.core/throw-on-conflicts!`)
  | `:router`    | Function of `routes opts => router` to override the actual router implementation"
  ([raw-routes]
   (router raw-routes {}))
  ([raw-routes opts]
   (let [{:keys [router] :as opts} (meta-merge default-router-options opts)
         routes (resolve-routes raw-routes opts)
         conflicting (conflicting-routes routes)
         routes (compile-routes routes opts)
         wilds? (boolean (some impl/wild-route? routes))
         all-wilds? (every? impl/wild-route? routes)
         router (cond
                  router router
                  (and (= 1 (count routes)) (not wilds?)) single-static-path-router
                  conflicting linear-router
                  (not wilds?) lookup-router
                  all-wilds? segment-router
                  :else mixed-router)]

     (when-let [validate (:validate opts)]
       (validate routes opts))

     (when-let [conflicts (:conflicts opts)]
       (when conflicting (conflicts conflicting)))

     (router routes opts))))
