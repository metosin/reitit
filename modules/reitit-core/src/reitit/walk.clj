(ns ^:no-doc  reitit.walk)

(defprotocol IKeywordize
  (-keywordize [coll]))

(defn- -keywordize-map
  [m]
  (persistent!
   (reduce-kv
    (fn [m k v]
      (if (string? k)
        (assoc! m (keyword k) (-keywordize v))
        (assoc! m (-keywordize k) (-keywordize v))))
    (transient (empty m))
    m)))

(defn- -keywordize-default
  [coll]
  (into (empty coll) (map -keywordize) coll))

(doseq [type [clojure.lang.PersistentHashSet
              clojure.lang.PersistentVector
              clojure.lang.PersistentQueue
              clojure.lang.PersistentStructMap
              clojure.lang.PersistentTreeMap
              clojure.lang.PersistentTreeSet]]
  (extend type IKeywordize {:-keywordize -keywordize-default}))

(doseq [type [clojure.lang.PersistentArrayMap
              clojure.lang.PersistentHashMap]]
  (extend type IKeywordize {:-keywordize -keywordize-map}))

(extend-protocol IKeywordize
  nil
  (-keywordize [_] nil)
  Object
  (-keywordize [x] x)
  clojure.lang.MapEntry
  (-keywordize [e] (clojure.lang.MapEntry/create
                    (-keywordize (.key e))
                    (-keywordize (.val e))))
  clojure.lang.ISeq
  (-keywordize [coll] (map -keywordize coll))
  clojure.lang.PersistentList
  (-keywordize [coll] (apply list (map -keywordize coll)))
  clojure.lang.PersistentList$EmptyList
  (-keywordize [x] x)
  clojure.lang.IRecord
  (-keywordize [r] (reduce (fn [r x] (conj r (-keywordize x))) r r)))

(defn keywordize-keys [m] (-keywordize m))
