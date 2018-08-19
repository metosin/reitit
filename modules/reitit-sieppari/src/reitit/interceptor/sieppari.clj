(ns reitit.interceptor.sieppari
  (:require [reitit.interceptor :as interceptor]
            [sieppari.queue :as queue]
            [sieppari.core :as sieppari]))

(def executor
  (reify
    interceptor/Executor
    (queue [_ interceptors]
      (queue/into-queue interceptors))
    (execute [_ interceptors request]
      (sieppari/execute interceptors request))
    (execute [_ interceptors request respond raise]
      (sieppari/execute interceptors request respond raise))))
