(ns example.middleware
  (:require [muuntaja.middleware]
            [ring.middleware.params]
            [reitit.middleware :as middleware]
            [reitit.ring.coercion :as rrc]))

;; unlift Middleware Record into vanilla Ring middleware
(defn wrap-coercion [handler resource]
  (middleware/chain
    [rrc/coerce-exceptions-middleware
     rrc/coerce-request-middleware
     rrc/coerce-response-middleware]
    handler
    resource))
