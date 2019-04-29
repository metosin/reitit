(ns reitit.core
  (:require [reitit.impl :as impl]
            [reitit.exception :as exception]
            [reitit.trie :as trie]))

;;
;; Expand
;;

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

;;
;; Router
;;

(defprotocol Router
  (router-name [this])
  (routes [this])
  (compiled-routes [this])
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
   (some-> match :path (cond-> (seq query-params) (str "?" (impl/query-string query-params))))))

;;
;; Different routers
;;

(defn linear-router
  "Creates a linear-router from resolved routes and optional
  expanded options. See [[router]] for available options, plus the following:

  | key                          | description |
  | -----------------------------|-------------|
  | `:reitit.trie/trie-compiler` | Optional trie-compiler.
  | `:reitit.trie/parameters`    | Optional function to create empty map(-like) path parameters value from sequence of keys."
  ([compiled-routes]
   (linear-router compiled-routes {}))
  ([compiled-routes opts]
   (let [compiler (::trie/trie-compiler opts (trie/compiler))
         names (impl/find-names compiled-routes opts)
         [pl nl] (reduce
                   (fn [[pl nl] [p {:keys [name] :as data} result]]
                     (let [{:keys [path-params] :as route} (impl/parse p)
                           f #(if-let [path (impl/path-for route %)]
                                (->Match p data result (impl/url-decode-coll %) path)
                                (->PartialMatch p data result (impl/url-decode-coll %) path-params))]
                       [(conj pl (-> (trie/insert nil p (->Match p data result nil nil) opts) (trie/compile)))
                        (if name (assoc nl name f) nl)]))
                   [[] {}]
                   compiled-routes)
         lookup (impl/fast-map nl)
         matcher (trie/linear-matcher compiler pl true)
         match-by-path (trie/path-matcher matcher compiler)
         routes (impl/uncompile-routes compiled-routes)]
     ^{:type ::router}
     (reify
       Router
       (router-name [_]
         :linear-router)
       (routes [_]
         routes)
       (compiled-routes [_]
         compiled-routes)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (if-let [match (match-by-path path)]
           (-> (:data match)
               (assoc :path-params (:params match))
               (assoc :path path))))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name path-params]
         (if-let [match (impl/fast-get lookup name)]
           (match (impl/path-params path-params))))))))

(defn lookup-router
  "Creates a lookup-router from resolved routes and optional
  expanded options. See [[router]] for available options."
  ([compiled-routes]
   (lookup-router compiled-routes {}))
  ([compiled-routes opts]
   (when-let [wilds (seq (filter impl/wild-route? compiled-routes))]
     (exception/fail!
       (str "can't create :lookup-router with wildcard routes: " wilds)
       {:wilds wilds
        :routes compiled-routes}))
   (let [names (impl/find-names compiled-routes opts)
         [pl nl] (reduce
                   (fn [[pl nl] [p {:keys [name] :as data} result]]
                     [(assoc pl p (->Match p data result {} p))
                      (if name
                        (assoc nl name #(->Match p data result % p))
                        nl)])
                   [{} {}]
                   compiled-routes)
         data (impl/fast-map pl)
         lookup (impl/fast-map nl)
         routes (impl/uncompile-routes compiled-routes)]
     ^{:type ::router}
     (reify Router
       (router-name [_]
         :lookup-router)
       (routes [_]
         routes)
       (compiled-routes [_]
         compiled-routes)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (impl/fast-get data path))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name path-params]
         (if-let [match (impl/fast-get lookup name)]
           (match (impl/path-params path-params))))))))

(defn trie-router
  "Creates a special prefix-tree router from resolved routes and optional
  expanded options. See [[router]] for available options, plus the following:

  | key                          | description |
  | -----------------------------|-------------|
  | `:reitit.trie/trie-compiler` | Optional trie-compiler.
  | `:reitit.trie/parameters`    | Optional function to create empty map(-like) path parameters value from sequence of keys."
  ([compiled-routes]
   (trie-router compiled-routes {}))
  ([compiled-routes opts]
   (let [compiler (::trie/trie-compiler opts (trie/compiler))
         names (impl/find-names compiled-routes opts)
         [pl nl] (reduce
                   (fn [[pl nl] [p {:keys [name] :as data} result]]
                     (let [{:keys [path-params] :as route} (impl/parse p)
                           f #(if-let [path (impl/path-for route %)]
                                (->Match p data result (impl/url-decode-coll %) path)
                                (->PartialMatch p data result (impl/url-decode-coll %) path-params))]
                       [(trie/insert pl p (->Match p data result nil nil) opts)
                        (if name (assoc nl name f) nl)]))
                   [nil {}]
                   compiled-routes)
         matcher (trie/compile pl compiler)
         match-by-path (trie/path-matcher matcher compiler)
         lookup (impl/fast-map nl)
         routes (impl/uncompile-routes compiled-routes)]
     ^{:type ::router}
     (reify
       Router
       (router-name [_]
         :trie-router)
       (routes [_]
         routes)
       (compiled-routes [_]
         compiled-routes)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (if-let [match (match-by-path path)]
           (-> (:data match)
               (assoc :path-params (:params match))
               (assoc :path path))))
       (match-by-name [_ name]
         (if-let [match (impl/fast-get lookup name)]
           (match nil)))
       (match-by-name [_ name path-params]
         (if-let [match (impl/fast-get lookup name)]
           (match (impl/path-params path-params))))))))

(defn single-static-path-router
  "Creates a fast router of 1 static route(s) and optional
  expanded options. See [[router]] for available options."
  ([compiled-routes]
   (single-static-path-router compiled-routes {}))
  ([compiled-routes opts]
   (when (or (not= (count compiled-routes) 1) (some impl/wild-route? compiled-routes))
     (exception/fail!
       (str ":single-static-path-router requires exactly 1 static route: " compiled-routes)
       {:routes compiled-routes}))
   (let [[n :as names] (impl/find-names compiled-routes opts)
         [[p data result]] compiled-routes
         p #?(:clj (.intern ^String p) :cljs p)
         match (->Match p data result {} p)
         routes (impl/uncompile-routes compiled-routes)]
     ^{:type ::router}
     (reify Router
       (router-name [_]
         :single-static-path-router)
       (routes [_]
         routes)
       (compiled-routes [_]
         compiled-routes)
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
       (match-by-name [_ name path-params]
         (if (= n name)
           (impl/fast-assoc match :path-params (impl/path-params path-params))))))))

(defn mixed-router
  "Creates two routers: [[lookup-router]] or [[single-static-path-router]] for
  static routes and [[segment-router]] for wildcard routes. All
  routes should be non-conflicting. Takes resolved routes and optional
  expanded options. See [[router]] for options."
  ([compiled-routes]
   (mixed-router compiled-routes {}))
  ([compiled-routes opts]
   (let [{wild true, lookup false} (group-by impl/wild-route? compiled-routes)
         ->static-router (if (= 1 (count lookup)) single-static-path-router lookup-router)
         wildcard-router (trie-router wild opts)
         static-router (->static-router lookup opts)
         names (impl/find-names compiled-routes opts)
         routes (impl/uncompile-routes compiled-routes)]
     ^{:type ::router}
     (reify Router
       (router-name [_]
         :mixed-router)
       (routes [_]
         routes)
       (compiled-routes [_]
         compiled-routes)
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

(defn quarantine-router
  "Creates two routers: [[mixed-router]] for non-conflicting routes
  and [[linear-router]] for conflicting routes. Takes resolved routes
  and optional expanded options. See [[router]] for options."
  ([compiled-routes]
   (quarantine-router compiled-routes {}))
  ([compiled-routes opts]
   (let [conflicting-paths (-> compiled-routes impl/path-conflicting-routes impl/conflicting-paths)
         conflicting? #(contains? conflicting-paths (first %))
         {conflicting true, non-conflicting false} (group-by conflicting? compiled-routes)
         linear-router (linear-router conflicting opts)
         mixed-router (mixed-router non-conflicting opts)
         names (impl/find-names compiled-routes opts)
         routes (impl/uncompile-routes compiled-routes)]
     ^{:type ::router}
     (reify Router
       (router-name [_]
         :quarantine-router)
       (routes [_]
         routes)
       (compiled-routes [_]
         compiled-routes)
       (options [_]
         opts)
       (route-names [_]
         names)
       (match-by-path [_ path]
         (or (match-by-path mixed-router path)
             (match-by-path linear-router path)))
       (match-by-name [_ name]
         (or (match-by-name mixed-router name)
             (match-by-name linear-router name)))
       (match-by-name [_ name path-params]
         (or (match-by-name mixed-router name path-params)
             (match-by-name linear-router name path-params)))))))

;;
;; Creating Routers
;;

(defn ^:no-doc default-router-options []
  {:lookup (fn lookup [[_ {:keys [name]}] _] (if name #{name}))
   :expand expand
   :coerce (fn coerce [route _] route)
   :compile (fn compile [[_ {:keys [handler]}] _] handler)
   :exception exception/exception
   :conflicts (fn throw! [conflicts] (exception/fail! :path-conflicts conflicts))})

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
  | `:conflicts` | Function of `{route #{route}} => ()` to handle conflicting routes
  | `:exception` | Function of `Exception => Exception ` to handle creation time exceptions (default `reitit.exception/exception`)
  | `:router`    | Function of `routes opts => router` to override the actual router implementation"
  ([raw-routes]
   (router raw-routes {}))
  ([raw-routes opts]
   (let [{:keys [router] :as opts} (merge (default-router-options) opts)]
     (try
       (let [routes (impl/resolve-routes raw-routes opts)
             path-conflicting (impl/path-conflicting-routes routes)
             name-conflicting (impl/name-conflicting-routes routes)
             compiled-routes (impl/compile-routes routes opts)
             wilds? (boolean (some impl/wild-route? compiled-routes))
             all-wilds? (every? impl/wild-route? compiled-routes)
             router (cond
                      router router
                      (and (= 1 (count compiled-routes)) (not wilds?)) single-static-path-router
                      path-conflicting quarantine-router
                      (not wilds?) lookup-router
                      all-wilds? trie-router
                      :else mixed-router)]

         (when-let [conflicts (:conflicts opts)]
           (when path-conflicting (conflicts path-conflicting)))

         (when name-conflicting
           (exception/fail! :name-conflicts name-conflicting))

         (when-let [validate (:validate opts)]
           (validate compiled-routes opts))

         (router compiled-routes opts))

       (catch #?(:clj Exception, :cljs js/Error) e
         (throw ((get opts :exception identity) e)))))))
