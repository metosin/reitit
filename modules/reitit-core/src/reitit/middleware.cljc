(ns reitit.middleware
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.core :as r]
            [reitit.impl :as impl]))

(defprotocol IntoMiddleware
  (into-middleware [this data opts]))

(defrecord Middleware [name wrap])
(defrecord Endpoint [data handler middleware])

(defn create [{:keys [name wrap compile] :as m}]
  (when (and wrap compile)
    (throw
      (ex-info
        (str "Middleware can't both :wrap and :compile defined " m) m)))
  (map->Middleware m))

(extend-protocol IntoMiddleware

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (into-middleware [[f & args] data opts]
    (if-let [{:keys [wrap] :as mw} (into-middleware f data opts)]
      (assoc mw :wrap #(apply wrap % args))))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (into-middleware [this _ _]
    (map->Middleware
      {:wrap this}))

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (into-middleware [this data opts]
    (into-middleware (create this) data opts))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (into-middleware [this data opts]
    (into-middleware (create this) data opts))

  Middleware
  (into-middleware [{:keys [wrap compile] :as this} data opts]
    (if-not compile
      this
      (if-let [middeware (into-middleware (compile data opts) data opts)]
        (map->Middleware
          (merge
            (dissoc this :create)
            (impl/strip-nils middeware))))))

  nil
  (into-middleware [_ _ _]))

(defn- ensure-handler! [path data scope]
  (when-not (:handler data)
    (throw (ex-info
             (str "path \"" path "\" doesn't have a :handler defined"
                  (if scope (str " for " scope)))
             (merge {:path path, :data data}
                    (if scope {:scope scope}))))))

(defn expand [middleware data opts]
  (->> middleware
       (keep #(into-middleware % data opts))
       (into [])))

(defn compile-handler [middleware handler]
  ((apply comp identity (keep :wrap middleware)) handler))

(defn compile-result
  ([route opts]
   (compile-result route opts nil))
  ([[path {:keys [middleware handler] :as data}]
    {:keys [::transform] :or {transform identity} :as opts} scope]
   (ensure-handler! path data scope)
   (let [middleware (expand (transform (expand middleware data opts)) data opts)]
     (map->Endpoint
       {:handler (compile-handler middleware handler)
        :middleware middleware
        :data data}))))

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for Middleware. See [docs](https://metosin.github.io/reitit/) for details.

  Example:

    (router
      [\"/api\" {:middleware [wrap-format wrap-oauth2]}
        [\"/users\" {:middleware [wrap-delete]
                     :handler get-user}]])

  See router options from [[reitit.core/router]]."
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:compile compile-result} opts)]
     (r/router data opts))))

(defn middleware-handler [router]
  (with-meta
    (fn [path]
      (some->> path
               (r/match-by-path router)
               :result
               :handler))
    {::router router}))

(defn chain
  "Creates a vanilla ring middleware chain out of sequence of
  IntoMiddleware thingies."
  ([middleware handler data]
   (chain middleware handler data nil))
  ([middleware handler data opts]
   (compile-handler (expand middleware data opts) handler)))
