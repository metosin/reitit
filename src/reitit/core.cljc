(ns reitit.core
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.regex :as regex]))

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

(defn resolve-routes [data opts]
  (->> (walk data opts) (map-meta merge-meta)))

(defprotocol Routing
  (match-route [this path])
  (path-for [this name] [this name parameters]))

(defrecord LinearRouter [routes]
  Routing
  (match-route [_ path]
    (reduce
      (fn [acc [p m matcher]]
        (if-let [params (matcher path)]
          (reduced (assoc m :route-params params))))
      nil routes)))

(defrecord LookupRouter [routes]
  Routing
  (match-route [_ path]
    (routes path)))

(defn router
  ([data]
   (router data {}))
  ([data opts]
   (let [routes (resolve-routes data opts)]
     (if (some regex/contains-wilds? (map first routes))
       (->LinearRouter
         (for [[p m] routes]
           [p m (regex/matcher p)]))
       (->LookupRouter
         (into {} routes))))))
