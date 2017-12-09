(ns example.server
  (:require [ring.adapter.jetty :as jetty]
            [reitit.middleware :as middleware]
            [reitit.ring.coercion-middleware :as coercion]))

(defonce ^:private server (atom nil))

;; unlift Middleware Record into vanilla Ring middleware
;; NOTE: to support format-based body coercion, an options map needs
;; to be set with :extract-request-format and extract-response-format
(defn wrap-coercion [handler resource]
  (middleware/chain
    [coercion/coerce-request-middleware
     coercion/coerce-response-middleware
     coercion/coerce-exceptions-middleware]
    handler
    resource))

(defn restart [handler]
  (let [app (-> handler
                (ring.middleware.params/wrap-params)
                (muuntaja.middleware/wrap-format))]
    (swap! server (fn [x]
                    (when x (.stop x))
                    (jetty/run-jetty
                      handler
                      {:port 3000, :join? false})))
    (println "server running in port 3000")))
