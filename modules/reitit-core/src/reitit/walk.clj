(ns ^:no-doc  reitit.walk)

(defprotocol Walkable
  (-walk [coll f]))

(extend-protocol Walkable
  nil
  (-walk [_ _] nil)
  Object
  (-walk [x _] x)
  clojure.lang.IMapEntry
  (-walk [e f] (clojure.lang.MapEntry. (f (.key e)) (f (.val e))))
  clojure.lang.ISeq
  (-walk [coll f] (map f coll))
  clojure.lang.PersistentList
  (-walk [coll f] (apply list (map f coll)))
  clojure.lang.PersistentList$EmptyList
  (-walk [x _] x)
  clojure.lang.IRecord
  (-walk [r f] (reduce (fn [r x] (conj r (f x))) r r)))

(defn- -walk-default
  [coll f]
  (into (empty coll) (map f) coll))

(doseq [type [clojure.lang.PersistentArrayMap
              clojure.lang.PersistentHashMap
              clojure.lang.PersistentHashSet
              clojure.lang.PersistentVector
              clojure.lang.PersistentQueue
              clojure.lang.PersistentStructMap
              clojure.lang.PersistentTreeMap
              clojure.lang.PersistentTreeSet]]
  (extend type Walkable {:-walk -walk-default}))

(defn walk
  [inner outer form]
  (outer (-walk form inner)))

(defn postwalk [f form] (walk (partial postwalk f) f form))

(defn- keywordize
  [m]
  (persistent!
   (reduce-kv
    (fn [m k v] (if (string? k) (assoc! m (keyword k) v) m))
    (transient {})
    m)))

(defn keywordize-keys
  [m]
  (postwalk (fn [x] (if (map? x) (keywordize m) x)) m))
