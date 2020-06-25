(ns reitit.swagger
  (:require [reitit.core :as r]
            [meta-merge.core :refer [meta-merge]]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.string :as str]
            [reitit.coercion :as coercion]
            [reitit.trie :as trie]))

(s/def ::id (s/or :keyword keyword? :set (s/coll-of keyword? :into #{})))
(s/def ::no-doc boolean?)
(s/def ::tags (s/coll-of (s/or :keyword keyword? :string string?) :kind #{}))
(s/def ::summary string?)
(s/def ::description string?)

(s/def ::swagger (s/keys :opt-un [::id]))
(s/def ::spec (s/keys :opt-un [::swagger ::no-doc ::tags ::summary ::description]))

(def swagger-feature
  "Feature for handling swagger-documentation for routes.
  Works both with Middleware & Interceptors. Does not participate
  in actual request processing, just provides specs for the new
  documentation keys for the route data. Should be accompanied by a
  [[swagger-spec-handler]] to expose the swagger spec.

  New route data keys contributing to swagger docs:

  | key           | description |
  | --------------|-------------|
  | :swagger      | map of any swagger-data. Must have `:id` (keyword or sequence of keywords) to identify the api
  | :no-doc       | optional boolean to exclude endpoint from api docs
  | :summary      | optional short string summary of an endpoint
  | :description  | optional long description of an endpoint. Supports http://spec.commonmark.org/

  Also the coercion keys contribute to swagger spec:

  | key           | description |
  | --------------|-------------|
  | :parameters   | optional input parameters for a route, in a format defined by the coercion
  | :responses    | optional descriptions of responses, in a format defined by coercion

  Example:

      [\"/api\"
       {:swagger {:id :my-api}
        :middleware [reitit.swagger/swagger-feature]}

       [\"/swagger.json\"
        {:get {:no-doc true
               :swagger {:info {:title \"my-api\"}}
               :handler reitit.swagger/swagger-spec-handler}}]

       [\"/plus\"
        {:get {:swagger {:tags \"math\"}
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

(defn- swagger-path [path opts]
  (-> path (trie/normalize opts) (str/replace #"\{\*" "{")))

(defn create-swagger-handler
  "Create a ring handler to emit swagger spec. Collects all routes from router which have
  an intersecting `[:swagger :id]` and which are not marked with `:no-doc` route data."
  []
  (fn create-swagger
    ([{::r/keys [router match] :keys [request-method]}]
     (let [{:keys [id] :or {id ::default} :as swagger} (-> match :result request-method :data :swagger)
           ids (trie/into-set id)
           strip-top-level-keys #(dissoc % :id :info :host :basePath :definitions :securityDefinitions)
           strip-endpoint-keys #(dissoc % :id :parameters :responses :summary :description)
           swagger (->> (strip-endpoint-keys swagger)
                        (merge {:swagger "2.0"
                                :x-id ids}))
           accept-route (fn [route]
                          (-> route second :swagger :id (or ::default) (trie/into-set) (set/intersection ids) seq))
           base-swagger-spec {:responses ^:displace {:default {:description ""}}}
           transform-endpoint (fn [[method {{:keys [coercion no-doc swagger] :as data} :data
                                            middleware :middleware
                                            interceptors :interceptors}]]
                                (if (and data (not no-doc))
                                  [method
                                   (meta-merge
                                     base-swagger-spec
                                     (apply meta-merge (keep (comp :swagger :data) middleware))
                                     (apply meta-merge (keep (comp :swagger :data) interceptors))
                                     (if coercion
                                       (coercion/get-apidocs coercion :swagger data))
                                     (select-keys data [:tags :summary :description])
                                     (strip-top-level-keys swagger))]))
           transform-path (fn [[p _ c]]
                            (if-let [endpoint (some->> c (keep transform-endpoint) (seq) (into {}))]
                              [(swagger-path p (r/options router)) endpoint]))
           map-in-order #(->> % (apply concat) (apply array-map))
           paths (->> router (r/compiled-routes) (filter accept-route) (map transform-path) map-in-order)]
       {:status 200
        :body (meta-merge swagger {:paths paths})}))
    ([req res raise]
     (try
       (res (create-swagger req))
       (catch #?(:clj Exception :cljs :default) e
         (raise e))))))
