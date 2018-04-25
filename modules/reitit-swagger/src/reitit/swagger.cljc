(ns reitit.swagger
  (:require [reitit.core :as r]
            [meta-merge.core :refer [meta-merge]]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [reitit.coercion :as coercion]))

(s/def ::id keyword?)
(s/def ::no-doc boolean?)
(s/def ::tags (s/coll-of (s/or :keyword keyword? :string string?) :kind #{}))
(s/def ::summary string?)
(s/def ::description string?)

(s/def ::swagger (s/keys :req-un [::id]))
(s/def ::spec (s/keys :opt-un [::swagger ::no-doc ::tags ::summary ::description]))

(def swagger-feature
  "Feature for handling swagger-documentation for routes.
  Works both with Middleware & Interceptors. Does not participate
  in actual request processing, just provides specs for the new
  documentation keys for the route data. Should be accompanied by a
  [[swagger-spec-handler]] to expose the swagger spec.

  Swagger-specific keys:

  | key           | description |
  | --------------|-------------|
  | :swagger      | map of any swagger-data. Must have `:id` to identify the api

  The following common keys also contribute to swagger spec:

  | key           | description |
  | --------------|-------------|
  | :no-doc       | optional boolean to exclude endpoint from api docs
  | :tags         | optional set of strings of keywords tags for an endpoint api docs
  | :summary      | optional short string summary of an endpoint
  | :description  | optional long description of an endpoint. Supports http://spec.commonmark.org/

  Also the coercion keys contribute to swagger spec:

  | :parameters   | optional input parameters for a route, in a format defined by the coercion
  | :responses    | optional descriptions of responess, in a format defined by coercion

  Example:

      [\"/api\"
       {:swagger {:id :my-api}
        :middleware [reitit.swagger/swagger-feature]}

       [\"/swagger.json\"
        {:get {:no-doc true
               :swagger {:info {:title \"my-api\"}}
               :handler reitit.swagger/swagger-spec-handler}}]

       [\"/plus\"
        {:get {:tags #{:math}
               :summary \"adds numbers together\"
               :description \"takes `x` and `y` query-params and adds them together\"
               :parameters {:query {:x int?, :y int?}}
               :responses {200 {:body {:total pos-int?}}}
               :handler (fn [{:keys [parameters]}]
                          {:status 200
                           :body (+ (-> parameters :query :x)
                                    (-> parameters :query :y)})}}]]"
  {:name ::swagger
   :spec ::spec})

(defn create-swagger-handler []
  "Create a ring handler to emit swagger spec."
  (fn [{:keys [::r/router ::r/match :request-method]}]
    (let [{:keys [id] :as swagger} (-> match :result request-method :data :swagger)
          swagger (set/rename-keys swagger {:id :x-id})
          accept-route #(-> % second :swagger :id (= id))
          transform-endpoint (fn [[method {{:keys [coercion no-doc swagger] :as data} :data}]]
                               (if (and data (not no-doc))
                                 [method
                                  (meta-merge
                                    (if coercion
                                      (coercion/-get-apidocs coercion :swagger data))
                                    (select-keys data [:tags :summary :description])
                                    (dissoc swagger :id))]))
          transform-path (fn [[p _ c]]
                           (if-let [endpoint (some->> c (keep transform-endpoint) (seq) (into {}))]
                             [p endpoint]))]
      (if id
        (let [paths (->> router (r/routes) (filter accept-route) (map transform-path) (into {}))]
          {:status 200
           :body (meta-merge swagger {:paths paths})})))))
