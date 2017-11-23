(ns reitit.segment
  (:require [reitit.impl :as impl]))

(defrecord Match [data params])

(defprotocol Segment
  (-insert [this ps data])
  (-lookup [this ps params]))

(extend-protocol Segment
  nil
  (-insert [this ps data])
  (-lookup [this ps params]))

;; TODO: catch-all
(defn- segment
  ([]
   (segment {} #{} nil))
  ([children wilds data]
   (let [children' (impl/fast-map children)]
     (reify
       Segment
       (-insert [_ [p & ps] data]
         (if-not p
           (segment children wilds data)
           (let [wild (impl/wild-param p)
                 wilds (if wild (conj wilds wild) wilds)
                 children (update children (or wild p) #(-insert (or % (segment)) ps data))]
             (segment children wilds nil))))
       (-lookup [_ [p & ps] params]
         (if (nil? p)
           (if data (assoc data :params params))
           (or (-lookup (impl/fast-get children' p) ps params)
               (some #(-lookup (impl/fast-get children' %) ps (assoc params % p)) wilds))))))))

(defn create [paths]
  (reduce
    (fn [segment [p data]]
      (let [ps (impl/segments p)]
        (-insert segment ps (map->Match {:data data}))))
    (segment) paths))

(defn lookup [segment ^String path]
  (let [ps (.split path "/")]
    (-lookup segment ps {})))

(comment
  (-> [["/:abba" 1]
       ["/kikka/*kakka" 2]]
      (create)
      (lookup "/kikka")
      (./aprint)))
