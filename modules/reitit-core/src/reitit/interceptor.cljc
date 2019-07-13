(ns reitit.interceptor
  (:require [meta-merge.core :refer [meta-merge]]
            [clojure.pprint :as pprint]
            [reitit.core :as r]
            [reitit.impl :as impl]
            [reitit.exception :as exception]))

(defprotocol IntoInterceptor
  (into-interceptor [this data opts]))

(defrecord Interceptor [name enter leave error])
(defrecord Endpoint [data interceptors queue])
(defrecord Context [request response exception])

(defprotocol Executor
  (queue
    [this interceptors]
    "takes a sequence of interceptors and compiles them to queue for the executor")
  (execute
    [this interceptors request]
    [this interceptors request respond raise]
    "executes the interceptor chain with a request")
  (enqueue
    [this context interceptors]
    "enqueues the interceptors into the queue"))

(defn context [request]
  (map->Context {:request request}))

(def ^:dynamic *max-compile-depth* 10)

(extend-protocol IntoInterceptor

  #?(:clj  clojure.lang.Keyword
     :cljs cljs.core.Keyword)
  (into-interceptor [this data {::keys [registry] :as opts}]
    (if-let [interceptor (if registry (registry this))]
      (into-interceptor interceptor data opts)
      (throw
        (ex-info
          (str
            "Interceptor " this " not found in registry.\n\n"
            (if (seq registry)
              (str
                "Available interceptors in registry:\n"
                (with-out-str
                  (pprint/print-table [:id :description] (for [[k v] registry] {:id k :description v}))))
              "see [reitit.interceptor/router] on how to add interceptor to the registry.\n") "\n")
          {:id this
           :registry registry}))))

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (into-interceptor [[f & args :as form] data opts]
    (when (and (seq args) (not (fn? f)))
      (exception/fail!
        (str "Invalid Interceptor form: " form "")
        {:form form}))
    (into-interceptor (apply f args) data opts))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (into-interceptor [this data opts]
    (into-interceptor
      {:name ::handler
       ::handler this
       :enter (fn [ctx]
                (assoc ctx :response (this (:request ctx))))}
      data opts))

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (into-interceptor [this data opts]
    (into-interceptor (map->Interceptor this) data opts))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (into-interceptor [this data opts]
    (into-interceptor (map->Interceptor this) data opts))

  Interceptor
  (into-interceptor [{:keys [compile] :as this} data opts]
    (if-not compile
      this
      (let [compiled (::compiled opts 0)
            opts (assoc opts ::compiled (inc ^long compiled))]
        (when (>= ^long compiled ^long *max-compile-depth*)
          (exception/fail!
            (str "Too deep Interceptor compilation - " compiled)
            {:this this, :data data, :opts opts}))
        (if-let [interceptor (into-interceptor (compile data opts) data opts)]
          (map->Interceptor
            (merge
              (dissoc this :compile)
              (impl/strip-nils interceptor)))))))

  nil
  (into-interceptor [_ _ _]))

;;
;; public api
;;

(defn chain
  "Creates a Interceptor chain out of sequence of IntoInterceptor
  Optionally takes route data and (Router) opts."
  ([interceptors]
   (chain interceptors nil nil))
  ([interceptors data]
   (chain interceptors data nil))
  ([interceptors data {::keys [transform] :or {transform identity} :as opts}]
   (let [transform (if (vector? transform) (apply comp (reverse transform)) transform)]
     (->> interceptors
          (keep #(into-interceptor % data opts))
          (transform)
          (keep #(into-interceptor % data opts))
          (into [])))))

(defn compile-result
  ([route opts]
   (compile-result route opts nil))
  ([[_ {:keys [interceptors handler] :as data}] {::keys [queue] :as opts} _]
   (let [chain (chain (into (vec interceptors) [handler]) data opts)]
     (map->Endpoint
       {:interceptors chain
        :queue ((or queue identity) chain)
        :data data}))))

(defn transform-butlast
  "Returns a function to that takes a interceptor transformation function and
  transforms all but last of the interceptors (e.g. the handler)"
  [f]
  (fn [interceptors]
    (concat
      (f (butlast interceptors))
      [(last interceptors)])))

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for Interceptors. See documentation on [[reitit.core/router]] for available options.
  In addition, the following options are available:

  Options:

  | key                             | description
  | --------------------------------|-------------
  | `:reitit.interceptor/transform` | Function or vector of functions of type `[Interceptor] => [Interceptor]` to transform the expanded Interceptors (default: identity).
  | `:reitit.interceptor/registry`  | Map of `keyword => IntoInterceptor` to replace keyword references into Interceptor

  Example:

      (router
        [\"/api\" {:interceptors [format-body oauth2]}
        [\"/users\" {:interceptors [delete]
                     :handler get-user}]])"
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:compile compile-result} opts)]
     (r/router data opts))))

(defn interceptor-handler [router]
  (with-meta
    (fn [path]
      (some->> (r/match-by-path router path)
               :result
               :interceptors))
    {::router router}))
