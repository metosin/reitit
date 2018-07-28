(ns ^:no-doc reitit.ring.middleware.multipart
  (:require [reitit.coercion :as coercion]
            [ring.middleware.multipart-params :as multipart-params]))

(def parameter-coercion
  {:multipart (coercion/->ParameterCoercion :multipart-params :string true true)})

(defn coerced-request [request coercers]
  (if-let [coerced (if coercers (coercion/coerce-request coercers request))]
    (update request :parameters merge coerced)
    request))

(defn create-multipart-middleware
  "Creates a Middleware to handle the multipart params, based on
  ring.middleware.multipart-params, taking same options. Mounts only
  if endpoint has `[:parameters :multipart]` defined. Publishes coerced
  parameters into `[:parameters :multipart]` under request."
  ([]
   (create-multipart-middleware nil))
  ([options]
   {:name ::multipart
    :compile (fn [{:keys [parameters coercion]} opts]
               (if-let [multipart (:multipart parameters)]
                 (let [opts (assoc opts ::coercion/parameter-coercion parameter-coercion)
                       coercers (if multipart (coercion/request-coercers coercion parameters opts))]
                   {:data {:swagger {:consumes #{"multipart/form-data"}}}
                    :wrap (fn [handler]
                            (fn
                              ([request]
                               (-> request
                                   (multipart-params/multipart-params-request options)
                                   (coerced-request coercers)
                                   (handler)))
                              ([request respond raise]
                               (-> request
                                   (multipart-params/multipart-params-request options)
                                   (coerced-request coercers)
                                   (handler respond raise)))))})))}))
