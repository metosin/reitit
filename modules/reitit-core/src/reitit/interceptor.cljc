(ns reitit.interceptor
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.core :as r]
            [reitit.impl :as impl]))

(defprotocol IntoInterceptor
  (into-interceptor [this data opts]))

(defrecord Interceptor [name enter leave error])
(defrecord Endpoint [data interceptors])

(def ^:dynamic *max-compile-depth* 10)

(extend-protocol IntoInterceptor

  #?(:clj  clojure.lang.Keyword
     :cljs cljs.core.Keyword)
  (into-interceptor [this data {:keys [::registry] :as opts}]
    (or (if-let [interceptor (if registry (registry this))]
          (into-interceptor interceptor data opts))
        (throw
          (ex-info
            (str "Interceptor " this " not found in registry.")
            {:keyword this
             :registry registry}))))

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (into-interceptor [[f & args :as form] data opts]
    (when (and (seq args) (not (fn? f)))
      (throw
        (ex-info
          (str "Invalid Interceptor form: " form "")
          {:form form})))
    (into-interceptor (apply f args) data opts))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (into-interceptor [this _ _]
    (map->Interceptor
      {:enter this}))

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
            opts (assoc opts ::compiled (inc compiled))]
        (when (>= compiled *max-compile-depth*)
          (throw
            (ex-info
              (str "Too deep Interceptor compilation - " compiled)
              {:this this, :data data, :opts opts})))
        (if-let [interceptor (into-interceptor (compile data opts) data opts)]
          (map->Interceptor
            (merge
              (dissoc this :create)
              (impl/strip-nils interceptor)))))))

  nil
  (into-interceptor [_ _ _]))

(defn- ensure-handler! [path data scope]
  (when-not (:handler data)
    (throw (ex-info
             (str "path \"" path "\" doesn't have a :handler defined"
                  (if scope (str " for " scope)))
             (merge {:path path, :data data}
                    (if scope {:scope scope}))))))

(defn- expand-and-transform
  [interceptors data {:keys [::transform] :or {transform identity} :as opts}]
  (->> interceptors
       (keep #(into-interceptor % data opts))
       (transform)
       (keep #(into-interceptor % data opts))
       (into [])))

;;
;; public api
;;

(defn chain
  "Creates a Interceptor chain out of sequence of IntoInterceptor
  and optionally a handler. Optionally takes route data and (Router) opts."
  ([interceptors handler data]
   (chain interceptors handler data nil))
  ([interceptors handler data opts]
   (let [interceptor (some-> (into-interceptor handler data opts)
                             (assoc :name (:name data)))]
     (-> (expand-and-transform interceptors data opts)
         (cond-> interceptor (conj interceptor))))))

(defn compile-result
  ([route opts]
   (compile-result route opts nil))
  ([[path {:keys [interceptors handler] :as data}] opts scope]
   (ensure-handler! path data scope)
   (map->Endpoint
     {:interceptors (chain interceptors handler data opts)
      :data data})))

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for Interceptors. See [docs](https://metosin.github.io/reitit/) for details.

  Example:

    (router
      [\"/api\" {:interceptors [format-body oauth2]}
        [\"/users\" {:interceptors [delete]
                     :handler get-user}]])

  Options:

  | key                             | description |
  | --------------------------------|-------------|
  | `:reitit.interceptor/transform` | Function of [Interceptor] => [Interceptor] to transform the expanded Interceptors (default: identity).
  | `:reitit.interceptor/registry`  | Map of `keyword => IntoInterceptor` to replace keyword references into Interceptor

  See router options from [[reitit.core/router]]."
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
