(ns ctrl.apply
  (:refer-clojure :exclude [apply])
  (:require [clojure.core :as c]))

(defn -match [path path-map]
  (letfn [(match [x f] (if (fn? f) (f x) (= x f)))]
    (reduce
     (fn [_ [ps f]]
       (let [match (loop [[p & pr] path, [pp & ppr] ps]
                     (cond (and p pp (match p pp)) (recur pr ppr)
                           (= nil p pp) true))]
         (when match (reduced f))))
     nil path-map)))

(defn -path-vals [m path-map]
  (letfn [(-path-vals [l p m]
            (reduce
             (fn [l [k v]]
               (let [p' (conj p k)
                     f (-match p' path-map)]
                 (cond
                   f (cons [p' (f v)] l)
                   (map? v) (-path-vals l p' v)
                   :else (cons [p' v] l))))
             l m))]
    (-path-vals [] [] m)))

(defn -assoc-in-path-vals [c]
  (reduce (partial c/apply assoc-in) {} c))

(defn any [_] true)

(defn apply [m path-map]
  (-> (-path-vals m path-map)
      (-assoc-in-path-vals)))
