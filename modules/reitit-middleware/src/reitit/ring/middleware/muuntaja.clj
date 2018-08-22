(ns reitit.ring.middleware.muuntaja
  (:require [muuntaja.core :as m]
            [muuntaja.middleware]
            [clojure.spec.alpha :as s]))

(s/def ::muuntaja m/muuntaja?)
(s/def ::spec (s/keys :opt-un [::muuntaja]))

(defn- displace [x] (with-meta x {:displace true}))

(def format-middleware
  "Middleware for content-negotiation, request and response formatting.

  Negotiates a request body based on `Content-Type` header and response body based on
  `Accept`, `Accept-Charset` headers. Publishes the negotiation results as `:muuntaja/request`
  and `:muuntaja/response` keys into the request.

  Decodes the request body into `:body-params` using the `:muuntaja/request` key in request
  if the `:body-params` doesn't already exist.

  Encodes the response body using the `:muuntaja/response` key in request if the response
  doesn't have `Content-Type` header already set.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set."
  {:name ::format
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:produces (displace (m/encodes muuntaja))
                                  :consumes (displace (m/decodes muuntaja))}}
                 :wrap #(muuntaja.middleware/wrap-format % muuntaja)}))})

(def format-negotiate-middleware
  "Middleware for content-negotiation.

  Negotiates a request body based on `Content-Type` header and response body based on
  `Accept`, `Accept-Charset` headers. Publishes the negotiation results as `:muuntaja/request`
  and `:muuntaja/response` keys into the request.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set."
  {:name ::format-negotiate
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:wrap #(muuntaja.middleware/wrap-format-negotiate % muuntaja)}))})

(def format-request-middleware
  "Middleware for request formatting.

  Decodes the request body into `:body-params` using the `:muuntaja/request` key in request
  if the `:body-params` doesn't already exist.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set."
  {:name ::format-request
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:consumes (displace (m/decodes muuntaja))}}
                 :wrap #(muuntaja.middleware/wrap-format-request % muuntaja)}))})

(def format-response-middleware
  "Middleware for response formatting.

  Encodes the response body using the `:muuntaja/response` key in request if the response
  doesn't have `Content-Type` header already set.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set."
  {:name ::format-response
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:produces (displace (m/encodes muuntaja))}}
                 :wrap #(muuntaja.middleware/wrap-format-response % muuntaja)}))})
