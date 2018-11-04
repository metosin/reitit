(ns reitit.segment
  (:refer-clojure :exclude [-lookup])
  (:require [reitit.impl :as impl]
            [clojure.string :as str]))

(defrecord Match [data path-params])

(defprotocol Segment
  (-insert [this ps data])
  (-lookup [this ps path-params]))

(extend-protocol Segment
  nil
  (-insert [_ _ _])
  (-lookup [_ _ _]))

(defn- -catch-all [children catch-all path-params p ps]
  (-lookup
    (impl/fast-get children catch-all)
    nil
    (assoc path-params catch-all (str/join "/" (cons p ps)))))

(defn- segment
  ([] (segment {} #{} nil nil))
  ([children wilds catch-all match]
   (let [children' (impl/fast-map children)
         wilds? (seq wilds)]
     ^{:type ::segment}
     (reify
       Segment
       (-insert [_ [p & ps] d]
         (if-not p
           (segment children wilds catch-all d)
           (let [[w c] ((juxt impl/wild-param impl/catch-all-param) p)
                 wilds (if w (conj wilds w) wilds)
                 catch-all (or c catch-all)
                 children (update children (or w c p) #(-insert (or % (segment)) ps d))]
             (segment children wilds catch-all match))))
       (-lookup [_ [p & ps] path-params]
         (if (nil? p)
           (when match (assoc match :path-params path-params))
           (or (-lookup (impl/fast-get children' p) ps path-params)
               (if wilds? (some #(-lookup (impl/fast-get children' %) ps (assoc path-params % p)) wilds))
               (if catch-all (-catch-all children' catch-all path-params p ps)))))))))

(defn insert [root path data]
  (-insert (or root (segment)) (impl/segments path) (map->Match {:data data})))

(defn create [paths]
  (reduce
    (fn [segment [p data]]
      (insert segment p data))
    nil paths))

(defn lookup [segment path]
  (-lookup segment (impl/segments path) {}))
