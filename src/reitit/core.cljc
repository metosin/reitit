(ns reitit.core)

(defn- deep-merge [& values]
  (let [[values strategy] (if (keyword? (first values))
                            [(rest values) (first values)]
                            [values :replace])]
    (cond
      (every? map? values)
      (apply merge-with (partial deep-merge strategy) values)

      (and (= strategy :into) (every? coll? values))
      (reduce into values)

      :else
      (last values))))

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

(defn merge-meta [key-strategy x]
  (reduce
    (fn [acc [k v]]
      (let [strategy (or (key-strategy k) :replace)]
        (deep-merge strategy acc {k v})))
    {}
    x))

(defn resolve-routes
  ([x]
   (resolve-routes (constantly :replace) x))
  ([key-strategy x]
   (->> x (walk) (map-meta (partial merge-meta key-strategy)))))
