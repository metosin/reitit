(ns reitit.pedestal
  (:require [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http :as http]
            [reitit.interceptor]
            [reitit.http])
  (:import (reitit.interceptor Executor)))

(defn- arity [f]
  (->> (class f)
       .getDeclaredMethods
       (filter #(= "invoke" (.getName %)))
       first
       .getParameterTypes
       alength))

(defn- error-with-arity-1? [{error-fn :error}]
  (and error-fn (= 1 (arity error-fn))))

(defn- error-arity-2->1 [error]
  (fn [context ex]
    (let [{ex :error :as context} (error (assoc context :error ex))]
      (if ex
        (-> context
            (assoc ::chain/error ex)
            (dissoc :error))
        context))))

(defn wrap-error-arity-2->1 [interceptor]
  (update interceptor :error error-arity-2->1))

(def pedestal-executor
  (reify
    Executor
    (queue [_ interceptors]
      (->> interceptors
           (map (fn [{:keys [::interceptor/handler] :as interceptor}]
                  (or handler interceptor)))
           (map (fn [interceptor]
                  (if (interceptor/interceptor? interceptor)
                    interceptor
                    (interceptor/interceptor
                     (if (error-with-arity-1? interceptor) 
                       (wrap-error-arity-2->1 interceptor)
                       interceptor)))))))
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
