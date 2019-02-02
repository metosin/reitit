(ns ^:no-doc reitit.impl
  #?(:cljs (:require-macros [reitit.impl]))
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [meta-merge.core :as mm]
            [reitit.trie :as trie])
  #?(:clj
     (:import (java.util.regex Pattern)
              (java.util HashMap Map)
              (java.net URLEncoder URLDecoder))))

(defn normalize [s]
  (-> s (trie/split-path) (trie/join-path)))

(defrecord Route [path path-parts path-params])

(defn parse [path]
  (let [path #?(:clj (.intern ^String (normalize path)) :cljs (normalize path))
        path-parts (trie/split-path path)
        path-params (->> path-parts (remove string?) (map :value) set)]
    (map->Route {:path-params path-params
                 :path-parts path-parts
                 :path path})))

(defn wild-route? [[path]]
  (-> path parse :path-params seq boolean))

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

(defn- -slice-start [[p1 :as p1s] [p2 :as p2s]]
  (let [-split (fn [p]
                 (if-let [i (and p (str/index-of p "/"))]
                   [(subs p 0 i) (subs p i)]
                   [p]))
        -slash (fn [cp p]
                 (cond
                   (not (string? cp)) [cp]
                   (and (string? cp) (not= (count cp) (count p))) [(subs p (count cp))]
                   (and (string? p) (not cp)) (-split p)))
        -postcut (fn [[p :as pps]]
                   (let [i (and p (str/index-of p "/"))]
                     (if (and i (pos? i))
                       (concat [(subs p 0 i) (subs p i)] (rest pps))
                       pps)))
        -tailcut (fn [cp [p :as ps]] (concat (-slash cp p) (rest ps)))]
    (if (or (nil? p1) (nil? p2))
      [(-postcut p1s) (-postcut p2s)]
      (let [cp (and (string? p1) (string? p2) (trie/common-prefix p1 p2))]
        [(-tailcut cp p1s) (-tailcut cp p2s)]))))

(defn- -slice-end [x xs]
  (let [i (if (string? x) (str/index-of x "/"))]
    (if (and (number? i) (pos? i))
      (concat [(subs x i)] xs)
      xs)))

(defn conflicting-routes? [route1 route2]
  (loop [parts1 (-> route1 first parse :path-parts)
         parts2 (-> route2 first parse :path-parts)]
    (let [[[s1 & ss1] [s2 & ss2]] (-slice-start parts1 parts2)]
      (cond
        (= s1 s2 nil) true
        (or (nil? s1) (nil? s2)) false
        (or (trie/catch-all? s1) (trie/catch-all? s2)) true
        (or (trie/wild? s1) (trie/wild? s2)) (recur (-slice-end s1 ss1) (-slice-end s2 ss2))
        (not= s1 s2) false
        :else (recur ss1 ss2)))))

(defn walk [raw-routes {:keys [path data routes expand]
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
      (mm/meta-merge acc {k v}))
    {} x))

(defn resolve-routes [raw-routes {:keys [coerce] :as opts}]
  (cond->> (->> (walk raw-routes opts) (map-data merge-data))
           coerce (into [] (keep #(coerce % opts)))))

(defn path-conflicting-routes [routes]
  (-> (into {}
            (comp (map-indexed (fn [index route]
                                 [route (into #{}
                                              (filter #(conflicting-routes? route %))
                                              (subvec routes (inc index)))]))
                  (filter (comp seq second)))
            routes)
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

(defn path-for [^Route route path-params]
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
      (throw
        (ex-info
          (str "missing path-params for route " template " -> " missing)
          {:path-params path-params, :required required})))))

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

(defn query-string
  "shallow transform of query parameters into query string"
  [params]
  (->> params
       (map (fn [[k v]]
              (str (form-encode (into-string k))
                   "="
                   (form-encode (into-string v)))))
       (str/join "&")))

(defmacro goog-extend [type base-type ctor & methods]
  `(do
     (def ~type (fn ~@ctor))

     (goog/inherits ~type ~base-type)

     ~@(map
         (fn [method]
           `(set! (.. ~type -prototype ~(symbol (str "-" (first method))))
                  (fn ~@(rest method))))
         methods)))
