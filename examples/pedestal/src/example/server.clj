(ns example.server
  (:require [io.pedestal.http :as http]
            [reitit.pedestal :as pedestal]
            [reitit.http :as reitit-http]
            [reitit.ring :as ring]))

(defn interceptor [x]
  {:enter (fn [ctx] (println ">>" x) ctx)
   :leave (fn [ctx] (println "<<" x) ctx)})

(def routing-interceptor
  (pedestal/routing-interceptor
    (reitit-http/router
      ["/api"
       {:interceptors [[interceptor :api]
                       [interceptor :apa]]}

       ["/ping"
        {:interceptors [[interceptor :ping]]
         :get {:interceptors [[interceptor :get]]
               :handler (fn [_]
                          (println "handler")
                          {:status 200,
                           :body "pong"})}}]]
      {:data {:interceptors [[interceptor :router]]}})
    (ring/create-default-handler)
    {:interceptors [[interceptor :top]]}))

(defonce server (atom nil))

(defn start []
  (when @server
    (http/stop @server)
    (println "server stopped"))
  (-> {:env :prod
       ::http/routes []
       ::http/resource-path "/public"
       ::http/type :jetty
       ::http/port 3000}
      (merge {:env :dev
              ::http/join? false
              ::http/allowed-origins {:creds true :allowed-origins (constantly true)}})
      (pedestal/default-interceptors routing-interceptor)
      http/dev-interceptors
      http/create-server
      http/start
      (->> (reset! server)))
  (println "server running in port 3000"))

(start)
