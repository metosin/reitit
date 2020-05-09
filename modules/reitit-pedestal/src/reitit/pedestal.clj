(ns reitit.pedestal
  (:require [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http :as http]
            [reitit.interceptor]
            [reitit.http])
  (:import (reitit.interceptor Executor)
           (java.lang.reflect Method)))

;; TODO: variadic
(defn- arities [f]
  (->> (class f)
       .getDeclaredMethods
       (filter (fn [^Method m] (= "invoke" (.getName m))))
       (map #(alength (.getParameterTypes ^Method %)))
       (set)))

(defn- error-without-arity-2? [{error-fn :error}]
  (and error-fn (not (contains? (arities error-fn) 2))))

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
    Executor
    (queue [_ interceptors]
      (->> interceptors
           (map (fn [{::interceptor/keys [handler] :as interceptor}]
                  (or handler interceptor)))
           (keep ->interceptor)))
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
