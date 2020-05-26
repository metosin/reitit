(ns reitit.http.coercion
  (:require [reitit.coercion :as coercion]
            [reitit.spec :as rs]
            [reitit.impl :as impl]))

(defn coerce-request-interceptor
  "Interceptor for pluggable request coercion.
  Expects a :coercion of type `reitit.coercion/Coercion`
  and :parameters from route data, otherwise does not mount."
  []
  {:name ::coerce-request
   :spec ::rs/parameters
   :compile (fn [{:keys [coercion parameters]} opts]
              (cond
                ;; no coercion, skip
                (not coercion) nil
                ;; just coercion, don't mount
                (not parameters) {}
                ;; mount
                :else
                (if-let [coercers (coercion/request-coercers coercion parameters opts)]
                  {:enter (fn [ctx]
                            (let [request (:request ctx)
                                  coerced (coercion/coerce-request coercers request)
                                  request (impl/fast-assoc request :parameters coerced)]
                              (assoc ctx :request request)))}
                  {})))})

(defn coerce-response-interceptor
  "Interceptor for pluggable response coercion.
  Expects a :coercion of type `reitit.coercion/Coercion`
  and :responses from route data, otherwise does not mount."
  []
  {:name ::coerce-response
   :spec ::rs/responses
   :compile (fn [{:keys [coercion responses]} opts]
              (cond
                ;; no coercion, skip
                (not coercion) nil
                ;; just coercion, don't mount
                (not responses) {}
                ;; mount
                :else
                (if-let [coercers (coercion/response-coercers coercion responses opts)]
                  {:leave (fn [ctx]
                            (let [request (:request ctx)
                                  response (:response ctx)
                                  response (coercion/coerce-response coercers request response)]
                              (assoc ctx :response response)))}
                  {})))})

(defn coerce-exceptions-interceptor
  "Interceptor for handling coercion exceptions.
  Expects a :coercion of type `reitit.coercion/Coercion`
  and :parameters or :responses from route data, otherwise does not mount."
  []
  {:name ::coerce-exceptions
   :compile (fn [{:keys [coercion parameters responses]} _]
              (if (and coercion (or parameters responses))
                {:error (fn [ctx]
                          (let [data (ex-data (:error ctx))]
                            (if-let [status (case (:type data)
                                              ::coercion/request-coercion 400
                                              ::coercion/response-coercion 500
                                              nil)]
                              (let [response {:status status, :body (coercion/encode-error data)}]
                                (-> ctx
                                    (assoc :response response)
                                    (assoc :error nil)))
                              ctx)))}))})
