(ns reitit.core
  (:require [meta-merge.core :refer [meta-merge]]))

(defprotocol ExpandArgs
  (expand [this]))

(extend-protocol ExpandArgs

  #?(:clj  clojure.lang.Keyword
     :cljs cljs.core.Keyword)
  (expand [this] {:handler this})

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (expand [this] this)

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (expand [this] this)

  nil
  (expand [_]))

(defn walk
  ([routes]
   (walk ["" []] routes))
  ([[pacc macc] routes]
   (letfn [(subwalk [p m r]
             (reduce #(into %1 (walk [p m] %2)) [] r))]
     (if (vector? (first routes))
       (subwalk pacc macc routes)
       (let [[path & [maybe-meta :as args]] routes]
         (let [[meta childs] (if (vector? maybe-meta)
                               [{} args]
                               [maybe-meta (rest args)])
               macc (into macc (expand meta))]
           (if (seq childs)
             (subwalk (str pacc path) macc childs)
             [[(str pacc path) macc]])))))))

(defn map-meta [f routes]
  (mapv #(update % 1 f) routes))

(defn merge-meta [x]
  (reduce
    (fn [acc [k v]]
      (meta-merge acc {k v}))
    {} x))

(defn resolve-routes [x]
  (->> x (walk) (map-meta merge-meta)))
