(ns reitit.interceptor
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.core :as r]
            [reitit.impl :as impl]))

(defprotocol IntoInterceptor
  (into-interceptor [this data opts]))

(defrecord Interceptor [name enter leave error])
(defrecord Endpoint [data interceptors])

(defn create [{:keys [name wrap compile] :as m}]
  (when (and wrap compile)
    (throw
      (ex-info
        (str "Interceptor can't have both :wrap and :compile defined " m) m)))
  (map->Interceptor m))

(def ^:dynamic *max-compile-depth* 10)

(extend-protocol IntoInterceptor

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (into-interceptor [[f & args] data opts]
    (if-let [{:keys [wrap] :as mw} (into-interceptor f data opts)]
      (assoc mw :wrap #(apply wrap % args))))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (into-interceptor [this _ _]
    (map->Interceptor
      {:enter this}))

  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs cljs.core.PersistentArrayMap)
  (into-interceptor [this data opts]
    (into-interceptor (create this) data opts))

  #?(:clj  clojure.lang.PersistentHashMap
     :cljs cljs.core.PersistentHashMap)
  (into-interceptor [this data opts]
    (into-interceptor (create this) data opts))

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

(defn expand [interceptors data opts]
  (->> interceptors
       (keep #(into-interceptor % data opts))
       (into [])))

(defn interceptor-chain [interceptors handler data opts]
  (expand (conj interceptors handler) data opts))

(defn compile-result
  ([route opts]
   (compile-result route opts nil))
  ([[path {:keys [interceptors handler] :as data}]
    {:keys [::transform] :or {transform identity} :as opts} scope]
   (ensure-handler! path data scope)
   (let [interceptors (expand (transform (expand interceptors data opts)) data opts)]
     (map->Endpoint
       {:interceptors (interceptor-chain interceptors handler data opts)
        :data data}))))

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for Interceptors. See [docs](https://metosin.github.io/reitit/) for details.

  Example:

    (router
      [\"/api\" {:interceptors [i/format i/oauth2]}
        [\"/users\" {:interceptors [i/delete]
                     :handler get-user}]])

  See router options from [[reitit.core/router]]."
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:compile compile-result} opts)]
     (r/router data opts))))

(defn interceptor-handler [router]
  (with-meta
    (fn [path]
      (some->> path
               (r/match-by-path router)
               :result
               :interceptors))
    {::router router}))

(comment
  (defn execute [r {{:keys [uri]} :request :as ctx}]
    (if-let [interceptors (-> (r/match-by-path r uri)
                              :result
                              :interceptors)]
      (as-> ctx $
            (reduce #(%2 %1) $ (keep :enter interceptors))
            (reduce #(%2 %1) $ (keep :leave interceptors)))))

  (def r
    (router
      ["/api" {:interceptors [{:name ::add
                               :enter (fn [ctx]
                                        (assoc ctx :enter true))
                               :leave (fn [ctx]
                                        (assoc ctx :leave true))}]}
       ["/ping" (fn [ctx] (assoc ctx :response "ok"))]]))

  (execute r {:request {:uri "/api/ping"}}))
