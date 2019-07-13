(ns reitit.middleware
  (:require [meta-merge.core :refer [meta-merge]]
            [clojure.pprint :as pprint]
            [reitit.core :as r]
            [reitit.impl :as impl]
            [reitit.exception :as exception]))

(defprotocol IntoMiddleware
  (into-middleware [this data opts]))

(defrecord Middleware [name wrap spec])
(defrecord Endpoint [data handler middleware])

(def ^:dynamic *max-compile-depth* 10)

(extend-protocol IntoMiddleware

  #?(:clj  clojure.lang.Keyword
     :cljs cljs.core.Keyword)
  (into-middleware [this data {::keys [registry] :as opts}]
    (if-let [middleware (if registry (registry this))]
      (into-middleware middleware data opts)
      (throw
        (ex-info
          (str
            "Middleware " this " not found in registry.\n\n"
            (if (seq registry)
              (str
                "Available middleware in registry:\n"
                (with-out-str
                  (pprint/print-table [:id :description] (for [[k v] registry] {:id k :description v}))))
              "see [reitit.middleware/router] on how to add middleware to the registry.\n") "\n")
          {:id this
           :registry registry}))))

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
    (into-middleware (map->Middleware this) data opts))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (into-middleware [this data opts]
    (into-middleware (map->Middleware this) data opts))

  Middleware
  (into-middleware [{:keys [compile] :as this} data opts]
    (if-not compile
      this
      (let [compiled (::compiled opts 0)
            opts (assoc opts ::compiled (inc ^long compiled))]
        (when (>= ^long compiled ^long *max-compile-depth*)
          (exception/fail!
            (str "Too deep Middleware compilation - " compiled)
            {:this this, :data data, :opts opts}))
        (if-let [middeware (into-middleware (compile data opts) data opts)]
          (map->Middleware
            (merge
              (dissoc this :compile)
              (impl/strip-nils middeware)))))))

  nil
  (into-middleware [_ _ _]))

(defn- ensure-handler! [path data scope]
  (when-not (:handler data)
    (exception/fail!
      (str "path \"" path "\" doesn't have a :handler defined"
           (if scope (str " for " scope)))
      (merge {:path path, :data data}
             (if scope {:scope scope})))))

(defn- expand-and-transform
  [middleware data {::keys [transform] :or {transform identity} :as opts}]
  (let [transform (if (vector? transform) (apply comp (reverse transform)) transform)]
    (->> middleware
         (keep #(into-middleware % data opts))
         (into [])
         (transform)
         (keep #(into-middleware % data opts))
         (into []))))

(defn- compile-handler [middleware handler]
  ((apply comp identity (keep :wrap middleware)) handler))

;;
;; public api
;;

(defn chain
  "Creates a Ring middleware chain out of sequence of IntoMiddleware
  and Handler. Optional takes route data and (Router) opts."
  ([middleware handler]
   (chain middleware handler nil))
  ([middleware handler data]
   (chain middleware handler data nil))
  ([middleware handler data opts]
   (compile-handler (expand-and-transform middleware data opts) handler)))

(defn compile-result
  ([route opts]
   (compile-result route opts nil))
  ([[path {:keys [middleware handler] :as data}] opts scope]
   (ensure-handler! path data scope)
   (let [middleware (expand-and-transform middleware data opts)]
     (map->Endpoint
       {:handler (compile-handler middleware handler)
        :middleware middleware
        :data data}))))

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for Middleware. See documentation on [[reitit.core/router]] for available options.
  In addition, the following options are available:

  | key                            | description
  | -------------------------------|-------------
  | `:reitit.middleware/transform` | Function or vector of functions of type `[Middleware] => [Middleware]` to transform the expanded Middleware (default: identity)
  | `:reitit.middleware/registry`  | Map of `keyword => IntoMiddleware` to replace keyword references into Middleware

  Example:

      (router
        [\"/api\" {:middleware [wrap-format wrap-oauth2]}
        [\"/users\" {:middleware [wrap-delete]
                     :handler get-user}]])"
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
