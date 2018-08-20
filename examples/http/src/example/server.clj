(ns example.server
  (:require [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.interceptor.sieppari]
            [ring.adapter.jetty :as jetty]))

(def app
  (http/ring-handler
    (http/router
      ["/" {:get (fn [request]
                   {:status 200
                    :body "hello!"})}])
    (ring/routes
      (ring/create-default-handler))
    {:executor reitit.interceptor.sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false, :async? true})
  (println "server running in port 3000"))

(comment
  (start))
