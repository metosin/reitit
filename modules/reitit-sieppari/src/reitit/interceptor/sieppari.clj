(ns reitit.interceptor.sieppari
  (:require [reitit.interceptor :as interceptor]
            [sieppari.core :as sieppari]
            [sieppari.queue :as queue]))

(def executor
  (reify
    interceptor/Executor
    (queue [_ interceptors]
      (queue/into-queue
       (map
        (fn [{::interceptor/keys [handler] :as interceptor}]
          (or handler interceptor))
        interceptors)))
    (execute [_ interceptors request]
      (sieppari/execute interceptors request))
    (execute [_ interceptors request respond raise]
      (sieppari/execute interceptors request respond raise))))
