(ns example.server
  (:require [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.interceptor.sieppari]
            [ring.adapter.jetty :as jetty]
            [clojure.core.async :as a]
            [manifold.deferred :as d]
            [promesa.core :as p]))

(defn interceptor [f x]
  {:enter (fn [ctx] (f (update-in ctx [:request :via] (fnil conj []) x)))
   :leave (fn [ctx] (f (update-in ctx [:response :body] str "\n<- " x)))})

(defn handler [f]
  (fn [{:keys [via]}]
    (f {:status 200,
        :body (str (apply str (map #(str "-> " % "\n") via)) "   hello!")})))

(def <sync> identity)
(def <future> #(future %))
(def <async> #(a/go %))
(def <deferred> d/success-deferred)
(def <promesa> p/promise)

(def app
  (http/ring-handler
    (http/router
      ["/api"
       {:interceptors [(interceptor <sync> :api)]}

       ["/sync"
        {:interceptors [(interceptor <sync> :sync)]
         :get {:interceptors [(interceptor <sync> :get)]
               :handler (handler <sync>)}}]

       ["/future"
        {:interceptors [(interceptor <future> :future)]
         :get {:interceptors [(interceptor <future> :get)]
               :handler (handler <future>)}}]

       ["/async"
        {:interceptors [(interceptor <async> :async)]
         :get {:interceptors [(interceptor <async> :get)]
               :handler (handler <async>)}}]

       ["/deferred"
        {:interceptors [(interceptor <deferred> :deferred)]
         :get {:interceptors [(interceptor <deferred> :get)]
               :handler (handler <deferred>)}}]

       ["/promesa"
        {:interceptors [(interceptor <promesa> :promesa)]
         :get {:interceptors [(interceptor <promesa> :get)]
               :handler (handler <promesa>)}}]])

    (ring/create-default-handler)
    {:executor reitit.interceptor.sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false, :async? true})
  (println "server running in port 3000"))

(comment
  (start))
