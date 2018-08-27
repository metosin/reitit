(ns example.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as coercion]
            [reitit.ring :as ring]
            [example.plain]
            [example.dspec]
            [example.schema]
            [example.spec]))

(def app
  (ring/ring-handler
    (ring/router
      [example.plain/routes
       example.schema/routes
       example.dspec/routes
       example.spec/routes]
      {:data {:muuntaja m/instance
      	      :middleware [params/wrap-params
                           muuntaja/format-middleware
                           coercion/coerce-exceptions-middleware
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware]}})
    (ring/create-default-handler)))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))

(comment
  (start))
