(ns reitit.openapi
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [meta-merge.core :refer [meta-merge]]
            [muuntaja.core :as m]
            [reitit.coercion :as coercion]
            [reitit.core :as r]
            [reitit.trie :as trie]))

(s/def ::id (s/or :keyword keyword? :set (s/coll-of keyword? :into #{})))
(s/def ::no-doc boolean?)
(s/def ::tags (s/coll-of (s/or :keyword keyword? :string string?)))
(s/def ::summary string?)
(s/def ::description string?)
(s/def :openapi/request-content-types (s/coll-of string?))
(s/def :openapi/response-content-types (s/coll-of string?))

(s/def ::openapi (s/keys :opt-un [::id]))
(s/def ::spec (s/keys :opt-un [::openapi ::no-doc ::tags ::summary ::description] :opt [:openapi/request-content-types :openapi/response-content-types]))

(def openapi-feature
  "Stability: alpha

  Feature for handling openapi-documentation for routes.
  Works both with Middleware & Interceptors. Does not participate
  in actual request processing, just provides specs for the new
  documentation keys for the route data. Should be accompanied by a
  [[create-openapi-handler]] to expose the openapi spec.

  New route data keys contributing to openapi docs:

  | key            | description |
  | ---------------|-------------|
  | :openapi       | map of any openapi-data. Can contain keys like `:deprecated`.
  | :openapi/request-content-types | vector of supported request content types. Defaults to `[\"application/json\"]` :response nnn :content :default. Only needed if you use the [:request :content :default] coercion.
  | :openapi/response-content-types | vector of supported response content types. Defaults to `[\"application/json\"]`. Only needed if you use the [:response nnn :content :default] coercion.
  | :no-doc        | optional boolean to exclude endpoint from api docs
  | :tags          | optional set of string or keyword tags for an endpoint api docs
  | :summary       | optional short string summary of an endpoint
  | :description   | optional long description of an endpoint. Supports http://spec.commonmark.org/

  Also the coercion keys contribute to openapi spec:

  | key           | description |
  | --------------|-------------|
  | :parameters   | optional input parameters for a route, in a format defined by the coercion
  | :responses    | optional descriptions of responses, in a format defined by coercion

  Use `:request` parameter coercion (instead of `:body`) to unlock per-content-type coercions.

  Example:

      [\"/api\"
       {:openapi {:id :my-api}
        :middleware [reitit.openapi/openapi-feature]}

       [\"/openapi.json\"
        {:get {:no-doc true
               :openapi {:info {:title \"my-api\"}}
               :handler (reitit.openapi/create-openapi-handler)}}]

       [\"/plus\"
        {:get {:openapi {:tags \"math\"}
               :summary \"adds numbers together\"
               :description \"takes `x` and `y` query-params and adds them together\"
               :parameters {:query {:x int?, :y int?}}
               :responses {200 {:body {:total pos-int?}}}
               :handler (fn [{:keys [parameters]}]
                          {:status 200
                           :body (+ (-> parameters :query :x)
                                    (-> parameters :query :y)})}}]]"
  {:name ::openapi
   :spec ::spec})

(defn- openapi-path [path opts]
  (-> path (trie/normalize opts) (str/replace #"\{\*" "{")))

(def ^:private form-content-type "application/x-www-form-urlencoded")

(defn -get-apidocs-openapi
  [coercion {:keys [request muuntaja parameters responses openapi/request-content-types openapi/response-content-types]} definitions]
  (let [{:keys [body form multipart]} parameters
        parameters (dissoc parameters :request :body :form :multipart)
        ->content (fn [data schema]
                    (merge
                     {:schema schema}
                     (select-keys data [:example :examples])
                     (:openapi data)))
        ->schema-object (fn [model opts]
                          (let [result (coercion/-get-model-apidocs
                                        coercion :openapi model
                                        (assoc opts :malli.json-schema/definitions-path "#/components/schemas/"))]
                            (when-let [d (:definitions result)]
                              (vswap! definitions merge d))
                            (dissoc result :definitions)))
        request-content-types (or request-content-types
                                  (when muuntaja (m/decodes muuntaja))
                                  ["application/json"])
        response-content-types (or response-content-types
                                  (when muuntaja (m/encodes muuntaja))
                                  ["application/json"])]
    (merge
     (when (seq parameters)
       {:parameters
        (->> (for [[in schema] parameters
                   :let [{:keys [properties required]} (->schema-object schema {:in in :type :parameter})
                         required? (partial contains? (set required))]
                   [k schema] properties]
               (merge {:in (name in)
                       :name k
                       :required (required? k)
                       :schema schema}
                      (select-keys schema [:description])))
             (into []))})
     (when body
       ;; :body uses a single schema to describe every :requestBody
       ;; the schema-object transformer should be able to transform into distinct content-types
       {:requestBody {:content (into {}
                                     (map (fn [content-type]
                                            (let [schema (->schema-object body {:in :requestBody
                                                                                :type :schema
                                                                                :content-type content-type})]
                                              [content-type {:schema schema}])))
                                     request-content-types)}})

     (when form
       ;; :form is similar to any other body, but the content type must be application/x-www-form-urlencoded
       {:requestBody {:content {form-content-type {:schema (->schema-object form
                                                                            {:in :requestBody
                                                                             :type :schema
                                                                             :content-type form-content-type})}}}})

     (when request
       ;; :request allows different :requestBody per content-type
       {:requestBody
        (merge
         (select-keys request [:description])
         {:content (merge
                    (when-let [{:keys [schema] :as data} (coercion/get-default request)]
                      (into {}
                            (map (fn [content-type]
                                   (let [schema (->schema-object schema {:in :requestBody
                                                                         :type :schema
                                                                         :content-type content-type})]
                                     [content-type (->content data schema)])))
                            request-content-types))
                    (into {}
                          (map (fn [[content-type {:keys [schema] :as data}]]
                                 (let [schema (->schema-object schema {:in :requestBody
                                                                       :type :schema
                                                                       :content-type content-type})]
                                   [content-type (->content data schema)])))
                          (dissoc (:content request) :default)))})})
     (when multipart
       {:requestBody
        {:content
         {"multipart/form-data"
          {:schema
           (->schema-object multipart {:in :requestBody
                                       :type :schema
                                       :content-type "multipart/form-data"})}}}})
     (when responses
       {:responses
        (into {}
              (map (fn [[status {:keys [content], :as response}]]
                     (let [default (coercion/get-default response)
                           content (-> (merge
                                        (when default
                                          (into {}
                                                (map (fn [content-type]
                                                       (let [schema (->schema-object (:schema default) {:in :responses
                                                                                                        :type :schema
                                                                                                        :content-type content-type})]
                                                         [content-type (->content default schema)])))
                                                response-content-types))
                                        (when content
                                          (into {}
                                                (map (fn [[content-type {:keys [schema] :as data}]]
                                                       (let [schema (->schema-object schema {:in :responses
                                                                                             :type :schema
                                                                                             :content-type content-type})]
                                                         [content-type (->content data schema)])))
                                                content)))
                                       (dissoc :default))]
                       [status (merge (select-keys response [:description])
                                      (when content
                                        {:content content}))]))
                   responses))}))))

(defn create-openapi-handler
  "Stability: alpha

  Create a ring handler to emit openapi spec. Collects all routes from router which have
  an intersecting `[:openapi :id]` and which are not marked with `:no-doc` route data."
  []
  (fn create-openapi
    ([{::r/keys [router match] :keys [request-method]}]
     (let [{:keys [id] :or {id ::default} :as openapi} (-> match :result request-method :data :openapi)
           ids (trie/into-set id)
           strip-top-level-keys #(dissoc % :id :info :host :basePath :definitions :securityDefinitions)
           strip-endpoint-keys #(dissoc % :id :parameters :responses :summary :description)
           openapi (->> (strip-endpoint-keys openapi)
                        (merge {:openapi "3.1.0"
                                :x-id ids}))
           accept-route (fn [route]
                          (-> route second :openapi :id (or ::default) (trie/into-set) (set/intersection ids) seq))
           definitions (volatile! {})
           transform-endpoint (fn [[method {{:keys [coercion no-doc openapi] :as data} :data
                                            middleware :middleware
                                            interceptors :interceptors}]]
                                (if (and data (not no-doc))
                                  [method
                                   (meta-merge
                                    (apply meta-merge (keep (comp :openapi :data) middleware))
                                    (apply meta-merge (keep (comp :openapi :data) interceptors))
                                    (if coercion
                                      (-get-apidocs-openapi coercion data definitions))
                                    (select-keys data [:tags :summary :description])
                                    (strip-top-level-keys openapi))]))
           transform-path (fn [[p _ c]]
                            (if-let [endpoint (some->> c (keep transform-endpoint) (seq) (into {}))]
                              [(openapi-path p (r/options router)) endpoint]))
           map-in-order #(->> % (apply concat) (apply array-map))
           paths (->> router (r/compiled-routes) (filter accept-route) (map transform-path) map-in-order)]
       {:status 200
        :body (cond-> (meta-merge openapi {:paths paths})
                (seq @definitions) (assoc-in [:components :schemas] @definitions))}))
    ([req res raise]
     (try
       (res (create-openapi req))
       (catch Exception e
         (raise e))))))
