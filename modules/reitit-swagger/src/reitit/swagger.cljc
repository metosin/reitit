(ns reitit.swagger
  (:require [reitit.core :as r]
            [meta-merge.core :refer [meta-merge]]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.string :as str]
            [reitit.ring :as ring]
            [reitit.coercion :as coercion]
    #?@(:clj [
            [jsonista.core :as j]])))

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
          swagger (->> (set/rename-keys swagger {:id :x-id})
                       (merge {:swagger "2.0"}))
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

#?(:clj
   (defn create-swagger-ui-handler
     "Creates a ring handler which can be used to serve swagger-ui.

     | key              | description |
     | -----------------|-------------|
     | :parameter       | optional name of the wildcard parameter, defaults to unnamed keyword `:`
     | :root            | optional resource root, defaults to `\"swagger-ui\"`
     | :url             | path to swagger endpoint, defaults to `/swagger.json`
     | :path            | optional path to mount the handler to. Works only if mounted outside of a router.
     | :config          | parameters passed to swaggger-ui, keys transformed into camelCase."
     ([]
      (create-swagger-ui-handler nil))
     ([options]
      (let [mixed-case (fn [k]
                         (let [[f & rest] (str/split (name k) #"-")]
                           (apply str (str/lower-case f) (map str/capitalize rest))))
            mixed-case-key (fn [[k v]] [(mixed-case k) v])
            config-json (fn [{:keys [url config]}] (j/write-value-as-string (merge config {:url url})))
            conf-js (fn [opts] (str "window.API_CONF = " (config-json opts) ";"))
            options (as-> options $
                          (update $ :root (fnil identity "swagger-ui"))
                          (update $ :url (fnil identity "/swagger.json"))
                          (update $ :config #(->> % (map mixed-case-key) (into {})))
                          (assoc $ :paths {"conf.js" {:headers {"Content-Type" "application/javascript"}
                                                      :status 200
                                                      :body (conf-js $)}
                                           "config.json" {:headers {"Content-Type" "application/json"}
                                                          :status 200
                                                          :body (config-json $)}}))]
        (ring/routes
          (ring/create-resource-handler options))))))
