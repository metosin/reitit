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

(defn- segments [^String path]
  (mapv
    (fn [^String p]
      (if (impl/wild-param? p) (-> p (subs 1) keyword) p))
    (.split path "/")))

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
           (let [wilds (if (keyword? p) (conj wilds p) wilds)
                 children (update children p #(-insert (or % (segment)) ps data))]
             (segment children wilds data))))
       (-lookup [_ [p & ps] params]
         (if (nil? p)
           (assoc data :params params)
           (or (-lookup (impl/fast-get children' p) ps params)
               (some #(-lookup (impl/fast-get children' %) ps (assoc params % p)) wilds))))))))

(defn create [paths]
  (reduce
    (fn [segment [p data]]
      (let [ps (segments p)]
        (-insert segment ps (map->Match {:data data}))))
    (segment) paths))

(defn lookup [segment ^String path]
  (let [ps (.split path "/")]
    (-lookup segment ps {})))
