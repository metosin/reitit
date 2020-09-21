(ns reitit.ring.middleware.parameters
  (:require [ring.middleware.params :as params]
            [ring.util.request :as req]))

(defn- form-params-request
  ([request]
   (wrap-form-params-request request {}))
  ([request options]
   (let [encoding (or (:encoding options)
                      (req/character-encoding request)
                      "UTF-8")]
     (if (:form-params request)
       request
       (params/assoc-form-params request encoding)))))

(defn- query-params-request
  ([request]
   (wrap-form-params-request request {}))
  ([request options]
   (let [encoding (or (:encoding options)
                      (req/character-encoding request)
                      "UTF-8")]
     (if (:query-params request)
       request
       (params/assoc-query-params request encoding)))))

(defn wrap-query-parameters
  "Add parameters from the query string to the request map as `query-params`."
  ([handler] (wrap-query-parameters handler {}))
  ([handler options]
   (fn ([request]
        (handler (query-params-request request options)))
     ([request respond raise]
      (handler (query-params-request request options) respond raise)))))

(defn wrap-form-parameters
  "Add parameters from the request body to the request map as `:form-params`."
  ([handler] (wrap-form-parameters handler {}))
  ([handler options]
   (fn ([request]
        (handler (form-params-request request options)))
     ([request respond raise]
        (handler (form-params-request request options) respond raise)))))

(def form-parameters-middleware
  "Middleware to parse urlencoded parameters from the form body (if the request is
  a url-encoded form). Adds the following keys to the request map:

  :form-params  - a map of parameters from the body
  :params       - a merged map of all types of parameter"
  {:name ::form-parameters
   :wrap wrap-form-parameters})

(def query-parameters-middleware
  "Middleware to parse urlencoded parameters from the query string. Adds the
  following keys to the request map:

  :query-params  - a map of parameters from the query string
  :params       - a merged map of all types of parameter"
  {:name ::form-parameters
   :wrap wrap-query-parameters})

(def parameters-middleware
  "Middleware to parse urlencoded parameters from the query string and form
  body (if the request is a url-encoded form). Adds the following keys to
  the request map:

  :query-params - a map of parameters from the query string
  :form-params  - a map of parameters from the body
  :params       - a merged map of all types of parameter"
  {:name ::parameters
   :wrap params/wrap-params})
