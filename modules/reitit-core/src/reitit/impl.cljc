(ns ^:no-doc reitit.impl
  #?(:cljs (:require-macros [reitit.impl]))
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [meta-merge.core :as mm]
            [reitit.trie :as trie]
            [reitit.exception :as exception]
            [reitit.exception :as ex])
  #?(:clj
     (:import (java.util.regex Pattern)
              (java.util HashMap Map)
              (java.net URLEncoder URLDecoder))))

(defn parse [path opts]
  (let [path #?(:clj (.intern ^String (trie/normalize path opts)) :cljs (trie/normalize path opts))
        path-parts (trie/split-path path opts)
        path-params (->> path-parts (remove string?) (map :value) set)]
    {:path-params path-params
     :path-parts path-parts
     :path path}))

(defn wild-path? [path opts]
  (-> path (parse opts) :path-params seq boolean))

(defn ->wild-route? [opts]
  (fn [[path]] (-> path (parse opts) :path-params seq boolean)))

(defn maybe-map-values
  "Applies a function to every value of a map, updates the value if not nil.
  Also works on vectors. Maintains key for maps, order for vectors."
  [f coll]
  (reduce-kv
    (fn [coll k v]
      (if-some [v' (f v)]
        (assoc coll k v')
        coll))
    coll
    coll))

(defn walk [raw-routes {:keys [path data routes expand endpoint]
                        :or {data [], routes []}
                        :as opts}]
  (letfn
    [(walk-many [p m r]
       (reduce #(into %1 (walk-one p m %2)) [] r))
     (walk-one [pacc macc routes]
       (if (vector? (first routes))
         (walk-many pacc macc routes)
         (when (string? (first routes))
           (let [[path & [maybe-arg :as args]] routes
                 [data childs] (if (or (vector? maybe-arg)
                                       (and (sequential? maybe-arg)
                                            (sequential? (first maybe-arg)))
                                       (nil? maybe-arg))
                                 [{} args]
                                 [maybe-arg (rest args)])]
             (let [d (endpoint pacc path macc data childs)
                   data-for-endpoint (when (:endpoint d) (into macc (expand (:endpoint d) opts)))
                   data-for-children (or (:inherit d) data)
                   macc (into macc (expand data-for-children opts))]
               (-> (when data-for-endpoint [[(str pacc path) data-for-endpoint]])
                   (concat (when (seq childs) (-> (str pacc path)
                                                  (walk-many macc (keep identity childs))
                                                  (seq))))))))))]
    (walk-one path (mapv identity data) raw-routes)))

(defn map-data [f routes]
  (mapv (fn [[p ds]] [p (f p ds)]) routes))

(defn merge-data [p x]
  (reduce
    (fn [acc [k v]]
      (try
        (mm/meta-merge acc {k v})
        (catch #?(:clj Exception, :cljs js/Error) e
          (ex/fail! ::merge-data {:path p, :left acc, :right {k v}, :exception e}))))
    {} x))

(defn resolve-routes [raw-routes {:keys [coerce] :as opts}]
  (cond->> (->> (walk raw-routes opts) (map-data merge-data))
    coerce (into [] (keep #(coerce % opts)))))

(defn path-conflicting-routes [routes opts]
  (-> (into {}
            (comp (map-indexed (fn [index route]
                                 [route (into #{}
                                              (filter #(trie/conflicting-paths? (first route) (first %) opts))
                                              (subvec routes (inc index)))]))
                  (filter (comp seq second)))
            routes)
      (not-empty)))

(defn unresolved-conflicts [path-conflicting]
  (-> (into {}
            (remove (fn [[[_ route-data] conflicts]]
                      (and (:conflicting route-data)
                           (every? (comp :conflicting second)
                                   conflicts))))
            path-conflicting)
      (not-empty)))

(defn conflicting-paths [conflicts]
  (->> (for [[p pc] conflicts]
         (conj (map first pc) (first p)))
       (apply concat)
       (set)))

(defn name-conflicting-routes [routes]
  (some->> routes
           (group-by (comp :name second))
           (remove (comp nil? first))
           (filter (comp pos? count butlast second))
           (seq)
           (map (fn [[k v]] [k (set v)]))
           (into {})))

(defn find-names [routes _]
  (into [] (keep #(-> % second :name)) routes))

(defn compile-route [[p m :as route] {:keys [compile] :as opts}]
  [p m (if compile (compile route opts))])

(defn compile-routes [routes opts]
  (into [] (keep #(compile-route % opts) routes)))

(defn uncompile-routes [routes]
  (mapv (comp vec (partial take 2)) routes))

(defn path-for [route path-params]
  (if (:path-params route)
    (if-let [parts (reduce
                     (fn [acc part]
                       (if (string? part)
                         (conj acc part)
                         (if-let [p (get path-params (:value part))]
                           (conj acc p)
                           (reduced nil))))
                     [] (:path-parts route))]
      (apply str parts))
    (:path route)))

(defn throw-on-missing-path-params [template required path-params]
  (when-not (every? #(contains? path-params %) required)
    (let [defined (-> path-params keys set)
          missing (set/difference required defined)]
      (exception/fail!
        (str "missing path-params for route " template " -> " missing)
        {:path-params path-params, :required required}))))

(defn fast-assoc
  #?@(:clj  [[^clojure.lang.Associative a k v] (.assoc a k v)]
      :cljs [[a k v] (assoc a k v)]))

(defn fast-map [m]
  #?(:clj  (let [m (or m {})] (HashMap. ^Map m))
     :cljs m))

(defn fast-get
  #?@(:clj  [[^HashMap m k] (.get m k)]
      :cljs [[m k] (m k)]))

(defn strip-nils [m]
  (->> m (remove (comp nil? second)) (into {})))

#?(:clj (def +percents+ (into [] (map #(format "%%%02X" %) (range 0 256)))))

#?(:clj (defn byte->percent [^long byte]
          (nth +percents+ (if (< byte 0) (+ 256 byte) byte))))

#?(:clj (defn percent-encode [^String s]
          (->> (.getBytes s "UTF-8") (map byte->percent) (str/join))))

;;
;; encoding & decoding
;;

;; + is safe, but removed so it would work the same as with js
(defn url-encode [s]
  (if s
    #?(:clj  (str/replace s #"[^A-Za-z0-9\!'\(\)\*_~.-]+" percent-encode)
       :cljs (js/encodeURIComponent s))))

(defn maybe-url-decode [s]
  (if s
    #?(:clj  (if (.contains ^String s "%")
               (URLDecoder/decode
                 (if (.contains ^String s "+")
                   (.replace ^String s "+" "%2B")
                   s)
                 "UTF-8"))
       :cljs (js/decodeURIComponent s))))

(defn url-decode [s]
  (or (maybe-url-decode s) s))

(defn form-encode [s]
  (if s
    #?(:clj  (URLEncoder/encode ^String s "UTF-8")
       :cljs (str/replace (js/encodeURIComponent s) "%20" "+"))))

(defn form-decode [s]
  (if s
    #?(:clj  (if (or (.contains ^String s "%") (.contains ^String s "+"))
               (URLDecoder/decode ^String s "UTF-8")
               s)
       :cljs (js/decodeURIComponent (str/replace s "+" " ")))))

(defn url-decode-coll
  "URL-decodes maps and vectors"
  [coll]
  (maybe-map-values maybe-url-decode coll))

(defprotocol IntoString
  (into-string [_]))

(extend-protocol IntoString
  #?(:clj  String
     :cljs string)
  (into-string [this] this)

  #?(:clj  clojure.lang.Keyword
     :cljs cljs.core.Keyword)
  (into-string [this]
    (let [ns (namespace this)]
      (str ns (if ns "/") (name this))))

  #?(:clj  Boolean
     :cljs boolean)
  (into-string [this] (str this))

  #?(:clj  Number
     :cljs number)
  (into-string [this] (str this))

  #?(:clj  Object
     :cljs object)
  (into-string [this] (str this))

  nil
  (into-string [_]))

(defn path-params
  "Convert parameters' values into URL-encoded strings, suitable for URL paths"
  [params]
  (maybe-map-values #(url-encode (into-string %)) params))

(defn- query-parameter [k v]
  (str (form-encode (into-string k))
       "="
       (form-encode (into-string v))))

(defn query-string
  "shallow transform of query parameters into query string"
  [params]
  (->> params
       (map (fn [[k v]]
              (if (or (sequential? v) (set? v))
                (str/join "&" (map query-parameter (repeat k) v))
                (query-parameter k v))))
       (str/join "&")))

(defn leaf-endpoint
  [_ _ _ data childs]
  (when-not (seq childs)
    {:endpoint data
     :inherit  {}}))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. `keys` is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn transform-inherited-data
  [transform-configs data]
  (let [short-hands {:consume #(dissoc-in %1 %2)
                     :inherit (fn [d _] d)}]
    (reduce
      (fn [acc {:keys [kss transform]}]
        (let [transform (or (get short-hands transform)
                            transform)]
          (reduce (fn [{:keys [inherit endpoint]} ks]
                    (if (get-in inherit ks)
                      {:inherit (transform inherit ks) :endpoint data}
                      {:inherit inherit :endpoint endpoint}))
                  acc
                  kss)))
      {:endpoint nil :inherit data}
      transform-configs)))

(defn mk-intermediate-endpoint-transform
  "Make function that returns map for valid endpoints, which are either a leaf path in
  the route tree, or have route data for one or more of nested key sequences.
  The returned map will contain data for the current endpoint under `:endpoint` and data to be
  inherited for children under `:inherit`.

  Provided arg should be a vector of maps with :kss, a seq of seq of keys, and :transform. Expanded route data
  will be queried with each of the key sequences. If data exists in the key sequence, the provided `:transform` function
  is called with `(transform data ks)`, so that the result is to be passed to children.
  Query is repeated for each seq of seq with the same transform, moving on to the next transform.
  All transforms are performed before passing data to children.

  If none of the key seqs match, then falsy is returned to indicate that the path should
  not be used as an endpoint.

  Optionally, `:transform` may be specified with keywords `:consume` as short-hand for removing the matching data,
  or `:inherit` for passing the data as-is to children."
  [transform-configs]
  (fn [prev-path path meta data childs]
    (or (leaf-endpoint prev-path path meta data childs)
        (and (map? data)
             (seq childs)
             (transform-inherited-data transform-configs data)))))

(defmacro goog-extend [type base-type ctor & methods]
  `(do
     (def ~type (fn ~@ctor))

     (goog/inherits ~type ~base-type)

     ~@(map
         (fn [method]
           `(set! (.. ~type -prototype ~(symbol (str "-" (first method))))
                  (fn ~@(rest method))))
         methods)))
