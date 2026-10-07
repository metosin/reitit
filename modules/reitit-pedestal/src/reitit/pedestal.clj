(ns reitit.pedestal
  (:require [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :as chain]
            [reitit.exception :as ex]
            [reitit.http]
            [reitit.interceptor])
  (:import (clojure.lang RestFn)
           (java.lang.reflect Method)))

(defn- required-arity
  "Number of positional parameters in an `:arglists` signature, ignoring any
   variadic tail: `[context ex]` -> 2, `[context & args]` -> 1."
  [arglist]
  (count (take-while #(not= '& %) arglist)))

(defn- variadic-arglist? [arglist]
  (boolean (some #(= '& %) arglist)))

(defn- declared-invoke-arities
  "Arities of the `invoke` methods declared on `f`'s class. Fns compiled by
   Clojure on the JVM only declare the arities they actually define, so this
   is a good approximation there. It says nothing about variadic arities,
   which are compiled to `doInvoke` instead."
  [f]
  (->> (class f)
       .getDeclaredMethods
       (filter (fn [^Method m] (= "invoke" (.getName m))))
       (map #(alength (.getParameterTypes ^Method %)))
       (set)))

(defn- signatures
  "Signatures `f` accepts, as `{:arity n, :variadic? bool}`, where `:arity` is
   the number of required positional parameters.

   `:arglists` metadata is preferred when present: it is the portable way to
   describe a fn's signatures, and unlike class reflection it is also correct
   on runtimes that represent every fn with a single shared class."
  [f]
  (if-let [arglists (:arglists (meta f))]
    (into #{} (map (fn [arglist]
                     {:arity (required-arity arglist)
                      :variadic? (variadic-arglist? arglist)}))
          arglists)
    (cond-> (into #{} (map (fn [n] {:arity n, :variadic? false}))
                  (declared-invoke-arities f))
      (instance? RestFn f) (conj {:arity (.getRequiredArity ^RestFn f)
                                  :variadic? true}))))

(defn- accepts-arity?
  "Whether `f` can be called with `n` arguments. Variadic signatures accept
   their required arity or more."
  [f n]
  (boolean (some (fn [{:keys [arity variadic?]}]
                   (if variadic? (<= arity n) (= arity n)))
                 (signatures f))))

(defn- error-without-arity-2? [{error-fn :error}]
  (and error-fn (not (accepts-arity? error-fn 2))))

(defn- error-arity-2->1 [error]
  (fn [context ex]
    (let [{ex :error :as context} (error (assoc context :error ex))]
      (if ex
        (-> context
            (assoc ::chain/error ex)
            (dissoc :error))
        context))))

(defn- wrap-error-arity-2->1 [interceptor]
  (update interceptor :error error-arity-2->1))

(defn ->interceptor [interceptor]
  (cond
    (interceptor/interceptor? interceptor)
    interceptor
    (->> (select-keys interceptor [:enter :leave :error]) (vals) (keep identity) (seq))
    (interceptor/interceptor
     (if (error-without-arity-2? interceptor)
       (wrap-error-arity-2->1 interceptor)
       interceptor))))

;;
;; Public API
;;

(def pedestal-executor
  (reify
    reitit.interceptor/Executor
    (queue [_ interceptors]
      (->> interceptors
           (map (fn [{::interceptor/keys [handler] :as interceptor}]
                  (or handler interceptor)))
           (keep ->interceptor)))
    (execute [_ _ _]
      (ex/unsupported-protocol-method! 'reitit.interceptor/execute))
    (execute [_ _ _ _ _]
      (ex/unsupported-protocol-method! 'reitit.interceptor/execute))
    (enqueue [_ context interceptors]
      (chain/enqueue context interceptors))))

(defn routing-interceptor
  ([router]
   (routing-interceptor router nil))
  ([router default-handler]
   (routing-interceptor router default-handler nil))
  ([router default-handler {:keys [interceptors]}]
   (interceptor/interceptor
    (reitit.http/routing-interceptor
     router
     default-handler
     {:executor pedestal-executor
      :interceptors interceptors}))))

(defn replace-last-interceptor [service-map interceptor]
  (-> service-map
      (update ::http/interceptors pop)
      (update ::http/interceptors conj interceptor)))
