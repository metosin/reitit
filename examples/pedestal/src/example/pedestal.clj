(ns example.pedestal
  (:require [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor :as interceptor]
            [reitit.http :as http])
  (:import (reitit.interceptor Executor)))

(def pedestal-executor
  (reify
    Executor
    (queue [_ interceptors]
      (->> interceptors
           (map (fn [{:keys [::interceptor/handler] :as interceptor}]
                  (or handler interceptor)))
           (map interceptor/interceptor)))
    (enqueue [_ context interceptors]
      (chain/enqueue context interceptors))))

(defn routing-interceptor
  ([router]
    (routing-interceptor router nil))
  ([router default-handler]
    (routing-interceptor router default-handler nil))
  ([router default-handler opts]
   (interceptor/interceptor
     (http/routing-interceptor
       router
       default-handler
       (merge {:executor pedestal-executor} opts)))))

(def router http/router)

(defn with-reitit-router [spec router]
  (-> spec
      (update ::http/interceptors (comp vec butlast))
      (update ::http/interceptors conj router)))
