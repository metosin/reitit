(ns reitit.middleware
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.core :as reitit]))

(defprotocol ExpandMiddleware
  (expand-middleware [this meta opts]))

(defrecord Middleware [name wrap create])

(defn create [{:keys [name gen wrap] :as m}]
  (when-not name
    (throw
      (ex-info
        (str "Middleware must have :name defined " m) m)))
  (when (and gen wrap)
    (throw
      (ex-info
        (str "Middleware can't both :wrap and :gen defined " m) m)))
  (map->Middleware m))

(extend-protocol ExpandMiddleware

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (expand-middleware [[f & args] meta opts]
    (if-let [mw (expand-middleware f meta opts)]
      (fn [handler]
        (apply mw handler args))))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (expand-middleware [this _ _] this)

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (expand-middleware [this meta opts]
    (expand-middleware (create this) meta opts))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (expand-middleware [this meta opts]
    (expand-middleware (create this) meta opts))

  Middleware
  (expand-middleware [{:keys [wrap gen]} meta opts]
    (if gen
      (if-let [wrap (gen meta opts)]
        (fn [handler & args]
          (apply wrap handler args)))
      (fn [handler & args]
        (apply wrap handler args))))

  nil
  (expand-middleware [_ _ _]))

(defn- ensure-handler! [path meta scope]
  (when-not (:handler meta)
    (throw (ex-info
             (str "path \"" path "\" doesn't have a :handler defined"
                  (if scope (str " for " scope)))
             (merge {:path path, :meta meta}
                    (if scope {:scope scope}))))))

(defn compose-middleware [middleware meta opts]
  (->> middleware
       (keep identity)
       (map #(expand-middleware % meta opts))
       (keep identity)
       (apply comp identity)))

(defn compile-handler
  ([route opts]
   (compile-handler route opts nil))
  ([[path {:keys [middleware handler] :as meta}] opts scope]
   (ensure-handler! path meta scope)
   ((compose-middleware middleware meta opts) handler)))

(defn router
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:compile compile-handler} opts)]
     (reitit/router data opts))))
