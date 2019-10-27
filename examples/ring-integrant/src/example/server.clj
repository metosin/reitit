(ns example.server
  (:require
   [reitit.ring :as ring]
   [ring.adapter.jetty :as jetty]
   [integrant.core :as ig]))

(def system-config
  {:example/jetty   {:port    3000
                     :join?   false
                     :handler (ig/ref :example/handler)}
   :example/handler {}})

(defmethod ig/init-key :example/jetty [_ {:keys [port join? handler]}]
  (println "server running in port" port)
  (jetty/run-jetty handler {:port port :join? join?}))

(defmethod ig/halt-key! :example/jetty [_ server]
  (.stop server))

(defmethod ig/init-key :example/handler [_ _]
  (ring/ring-handler
   (ring/router
    ["/ping" {:get {:handler (fn [_] {:status 200 :body "pong!"})}}])))

(defn -main []
  (ig/init system-config))
