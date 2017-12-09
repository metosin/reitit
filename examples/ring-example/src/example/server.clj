(ns example.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params]
            [muuntaja.middleware]
            [reitit.ring :as ring]
            [reitit.ring.coercion-middleware :as coercion]
            [example.dspec]
            [example.schema]
            [example.spec]))

(defonce ^:private server (atom nil))

(def app
  (ring/ring-handler
    (ring/router
      [example.schema/routes
       example.dspec/routes
       example.spec/routes]
      {:data {:middleware [ring.middleware.params/wrap-params
                           muuntaja.middleware/wrap-format
                           coercion/coerce-exceptions-middleware
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware]}})))

(defn restart []
  (swap! server (fn [x]
                  (when x (.stop x))
                  (jetty/run-jetty
                    app
                    {:port 3000, :join? false})))
  (println "server running in port 3000"))

(restart)
