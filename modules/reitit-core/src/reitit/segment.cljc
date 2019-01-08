(ns reitit.segment
  (:refer-clojure :exclude [-lookup])
  (:require [reitit.impl :as impl]
            [clojure.string :as str])
  #?(:clj (:import (reitit Trie Trie$Match))))

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
               (if (and wilds? (not (str/blank? p))) (some #(-lookup (impl/fast-get children' %) ps (assoc path-params % p)) wilds))
               (if catch-all (-catch-all children' catch-all path-params p ps)))))))))

(defn insert [root path data]
  #?(:cljs (-insert (or root (segment)) (impl/segments path) (map->Match {:data data}))
     :clj (.add (or ^Trie root ^Trie (Trie.)) ^String path data)))

(defn create [paths]
  (reduce
    (fn [segment [p data]]
      #?(:cljs (insert segment p data)
         :clj  (.add ^Trie segment ^String p data)))
    #?(:cljs nil
       :clj  (Trie.))
    paths))

(defn lookup [segment path]
  #?(:cljs (-lookup segment (impl/segments path) {})
     :clj  (if-let [match ^Trie$Match (Trie/lookup segment path)]
             (->Match (.data match) (clojure.lang.PersistentHashMap/create (.params match))))))
