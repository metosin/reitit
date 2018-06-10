(ns example.server
  (:require [ring.adapter.jetty :as jetty]
            [muuntaja.middleware]
            [ring.middleware.params]))

(defonce ^:private server (atom nil))

(defn restart [handler]
  (let [app (-> handler
                (ring.middleware.params/wrap-params)
                (muuntaja.middleware/wrap-format))]
    (swap! server (fn [x]
                    (when x (.stop x))
                    (jetty/run-jetty
                      app
                      {:port 3000, :join? false})))
    (println "server running in port 3000")))
