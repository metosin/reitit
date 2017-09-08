(ns reitit.middleware
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.core :as reitit]))

(defprotocol IntoMiddleware
  (into-middleware [this meta opts]))

(defrecord Middleware [name wrap])
(defrecord Endpoint [meta handler middleware])

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

(extend-protocol IntoMiddleware

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (into-middleware [[f & args] meta opts]
    (if-let [{:keys [wrap] :as mw} (into-middleware f meta opts)]
      (assoc mw :wrap #(apply wrap % args))))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (into-middleware [this _ _]
    (map->Middleware
      {:wrap this}))

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (into-middleware [this meta opts]
    (into-middleware (create this) meta opts))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (into-middleware [this meta opts]
    (into-middleware (create this) meta opts))

  Middleware
  (into-middleware [{:keys [wrap gen] :as this} meta opts]
    (if-not gen
      this
      (if-let [wrap (gen meta opts)]
        (map->Middleware
          (-> this
              (dissoc :gen)
              (assoc :wrap wrap))))))

  nil
  (into-middleware [_ _ _]))

(defn- ensure-handler! [path meta scope]
  (when-not (:handler meta)
    (throw (ex-info
             (str "path \"" path "\" doesn't have a :handler defined"
                  (if scope (str " for " scope)))
             (merge {:path path, :meta meta}
                    (if scope {:scope scope}))))))

(defn expand [middleware meta opts]
  (->> middleware
       (keep #(into-middleware % meta opts))
       (into [])))

(defn compile-handler [middleware handler]
  ((apply comp identity (keep :wrap middleware)) handler))

(compile-handler
  [(map->Middleware
     {:wrap
      (fn [handler]
        (fn [request]
          (handler request)))})] identity)

(defn compile-result
  ([route opts]
   (compile-result route opts nil))
  ([[path {:keys [middleware handler] :as meta}] opts scope]
   (ensure-handler! path meta scope)
   (let [middleware (expand middleware meta opts)]
     (map->Endpoint
       {:handler (compile-handler middleware handler)
        :middleware middleware
        :meta meta}))))

(defn router
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:compile compile-result} opts)]
     (reitit/router data opts))))

(defn middleware-handler [router]
  (with-meta
    (fn [path]
      (some->> path
               (reitit/match-by-path router)
               :result
               :handler))
    {::router router}))
