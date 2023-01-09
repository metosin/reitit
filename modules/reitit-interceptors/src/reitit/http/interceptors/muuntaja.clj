(ns reitit.http.interceptors.muuntaja
  (:require [clojure.spec.alpha :as s]
            [muuntaja.core :as m]
            [muuntaja.interceptor]))

(s/def ::muuntaja m/muuntaja?)
(s/def ::spec (s/keys :opt-un [::muuntaja]))

(defn- displace [x] (with-meta x {:displace true}))
(defn- stripped [x] (select-keys x [:enter :leave :error]))

(defn- publish-swagger-data? [{:keys [form body]}]
  (not (and (some? form) (nil? body))))

(defn format-interceptor
  "Interceptor for content-negotiation, request and response formatting.

  Negotiates a request body based on `Content-Type` header and response body based on
  `Accept`, `Accept-Charset` headers. Publishes the negotiation results as `:muuntaja/request`
  and `:muuntaja/response` keys into the request.

  Decodes the request body into `:body-params` using the `:muuntaja/request` key in request
  if the `:body-params` doesn't already exist.

  Encodes the response body using the `:muuntaja/response` key in request if the response
  doesn't have `Content-Type` header already set.

  Swagger-data will be omitted when `:form`, but no `:body`, parameters are defined.

  Optionally takes a default muuntaja instance as argument.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set."
  ([]
   (format-interceptor nil))
  ([default-muuntaja]
   {:name ::format
    :spec ::spec
    :compile (fn [{:keys [muuntaja parameters]} _]
               (if-let [muuntaja (or muuntaja default-muuntaja)]
                 (merge
                  (stripped (muuntaja.interceptor/format-interceptor muuntaja))
                  (if (publish-swagger-data? parameters)
                    {:data {:swagger {:produces (displace (m/encodes muuntaja))
                                      :consumes (displace (m/decodes muuntaja))}}}))))}))

(defn format-negotiate-interceptor
  "Interceptor for content-negotiation.

  Negotiates a request body based on `Content-Type` header and response body based on
  `Accept`, `Accept-Charset` headers. Publishes the negotiation results as `:muuntaja/request`
  and `:muuntaja/response` keys into the request.

  Optionally takes a default muuntaja instance as argument.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set."
  ([]
   (format-negotiate-interceptor nil))
  ([default-muuntaja]
   {:name ::format-negotiate
    :spec ::spec
    :compile (fn [{:keys [muuntaja]} _]
               (if-let [muuntaja (or muuntaja default-muuntaja)]
                 (stripped (muuntaja.interceptor/format-negotiate-interceptor muuntaja))))}))

(defn format-request-interceptor
  "Interceptor for request formatting.

  Decodes the request body into `:body-params` using the `:muuntaja/request` key in request
  if the `:body-params` doesn't already exist.

  Swagger-data will be omitted when `:form`, but no `:body`, parameters are defined.

  Optionally takes a default muuntaja instance as argument.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set."
  ([]
   (format-request-interceptor nil))
  ([default-muuntaja]
   {:name ::format-request
    :spec ::spec
    :compile (fn [{:keys [muuntaja parameters]} _]
               (if-let [muuntaja (or muuntaja default-muuntaja)]
                 (merge
                  (stripped (muuntaja.interceptor/format-request-interceptor muuntaja))
                  (when (publish-swagger-data? parameters)
                    {:data {:swagger {:consumes (displace (m/decodes muuntaja))}}}))))}))

(defn format-response-interceptor
  "Interceptor for response formatting.

  Encodes the response body using the `:muuntaja/response` key in request if the response
  doesn't have `Content-Type` header already set.

  Swagger-data will be omitted when `:form`, but no `:body`, parameters are defined.

  Optionally takes a default muuntaja instance as argument.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set."
  ([]
   (format-response-interceptor nil))
  ([default-muuntaja]
   {:name ::format-response
    :spec ::spec
    :compile (fn [{:keys [muuntaja parameters]} _]
               (if-let [muuntaja (or muuntaja default-muuntaja)]
                 (merge
                  (stripped (muuntaja.interceptor/format-response-interceptor muuntaja))
                  (when (publish-swagger-data? parameters)
                    {:data {:swagger {:produces (displace (m/encodes muuntaja))}}}))))}))
