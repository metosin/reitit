(ns reitit.segment
  (:require [reitit.impl :as impl]
            [clojure.string :as str]))

(defrecord Match [data params])

(defprotocol Segment
  (-insert [this ps data])
  (-lookup [this ps params]))

(extend-protocol Segment
  nil
  (-insert [this ps data])
  (-lookup [this ps params]))

(defn- -catch-all [catch-all data params p ps]
  (if catch-all
    (assoc data :params (assoc params catch-all (str/join "/" (cons p ps))))))

(defn- segment
  ([] (segment {} #{} nil nil))
  ([children wilds catch-all data]
   (let [children' (impl/fast-map children)]
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
             (segment children wilds catch-all data))))
       (-lookup [_ [p & ps] params]
         (if (nil? p)
           (if data (assoc data :params params))
           (or (-lookup (impl/fast-get children' p) ps params)
               (some #(-lookup (impl/fast-get children' %) ps (assoc params % p)) wilds)
               (-catch-all catch-all data params p ps))))))))

(defn insert [root path data]
  (-insert (or root (segment)) (impl/segments path) (map->Match {:data data})))

(defn create [paths]
  (reduce
    (fn [segment [p data]]
      (insert segment p data))
    nil paths))

(defn lookup [segment ^String path]
  (-lookup segment (.split path "/") {}))

(comment
  (-> [["/:abba" 1]
       ["/:abba/:dabba" 2]
       ["/kikka/*kakka" 3]]
      (create)
      (lookup "/kikka/1/2")
      (./aprint)))
