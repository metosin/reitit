(ns reitit.ring.middleware.parameters
  (:require [ring.middleware.params :as params]))

(def parameters-middleware
  "Middleware to parse urlencoded parameters from the query string and form
  body (if the request is a url-encoded form). Adds the following keys to
  the request map:

  :query-params - a map of parameters from the query string
  :form-params  - a map of parameters from the body
  :params       - a merged map of all types of parameter"
  {:name ::parameters
   :wrap params/wrap-params})
