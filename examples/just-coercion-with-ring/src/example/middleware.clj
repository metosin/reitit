(ns example.middleware
  (:require [muuntaja.middleware]
            [ring.middleware.params]
            [reitit.middleware :as middleware]
            [reitit.ring.coercion :as rrc]))

;; unlift Middleware Record into vanilla Ring middlewareL
;; NOTE: to support format-based body coercion, an options map needs
;; to be set with :extract-request-format and extract-response-format
(defn wrap-coercion [handler resource]
  (middleware/chain
    [rrc/coerce-exceptions-middleware
     rrc/coerce-request-middleware
     rrc/coerce-response-middleware]
    handler
    resource
    {:extract-request-format (comp :format :muuntaja/request)
     :extract-response-format (comp :format :muuntaja/response)}))
