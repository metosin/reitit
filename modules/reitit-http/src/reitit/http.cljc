(ns reitit.http
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.interceptor :as interceptor]
            [reitit.ring :as ring]
            [reitit.core :as r]
            [reitit.impl :as impl]))

(defrecord Endpoint [data interceptors queue handler path method])

(defn coerce-handler [[path data] {:keys [expand] :as opts}]
  [path (reduce
          (fn [acc method]
            (if (contains? acc method)
              (update acc method expand opts)
              acc)) data ring/http-methods)])

(defn compile-result [[path data] {:keys [::default-options-handler] :as opts}]
  (let [[top childs] (ring/group-keys data)
        compile (fn [[path data] opts scope]
                  (interceptor/compile-result [path data] opts scope))
        ->endpoint (fn [p d m s]
                     (let [compiled (compile [p d] opts s)]
                       (-> compiled
                           (map->Endpoint)
                           (assoc :path p)
                           (assoc :method m))))
        ->methods (fn [any? data]
                    (reduce
                      (fn [acc method]
                        (cond-> acc
                                any? (assoc method (->endpoint path data method nil))))
                      (ring/map->Methods
                        {:options
                         (if default-options-handler
                           (->endpoint path (assoc data
                                              :handler default-options-handler
                                              :no-doc true) :options nil))})
                      ring/http-methods))]
    (if-not (seq childs)
      (->methods true top)
      (reduce-kv
        (fn [acc method data]
          (let [data (meta-merge top data)]
            (assoc acc method (->endpoint path data method method))))
        (->methods (:handler top) data)
        childs))))

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for http-methods and Interceptors. See [docs](https://metosin.github.io/reitit/)
  for details.

  Options:

  | key                                    | description |
  | ---------------------------------------|-------------|
  | `:reitit.interceptor/transform`         | Function of `[Interceptor] => [Interceptor]` to transform the expanded Interceptors (default: identity).
  | `:reitit.interceptor/registry`          | Map of `keyword => IntoInterceptor` to replace keyword references into Interceptors
  | `:reitit.http/default-options-handler` | Default handler for `:options` method in endpoints (default: reitit.ring/default-options-handler)

  Example:

      (router
        [\"/api\" {:interceptors [format-i oauth2-i]}
          [\"/users\" {:get get-user
                       :post update-user
                       :delete {:interceptors [delete-i]
                               :handler delete-user}}]])

  See router options from [[reitit.core/router]] and [[reitit.middleware/router]]."
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (merge {:coerce coerce-handler
                      :compile compile-result
                      ::default-options-handler ring/default-options-handler} opts)]
     (r/router data opts))))

(defn routing-interceptor
  "A Pedestal-style routing interceptor that enqueus the interceptors into context."
  [router default-handler {:keys [interceptors executor]}]
  (let [default-handler (or default-handler (fn ([_])))
        default-interceptors (->> interceptors
                                  (map #(interceptor/into-interceptor % nil (r/options router))))
        default-queue (interceptor/queue executor default-interceptors)]
    {:name ::router
     :enter (fn [{:keys [request] :as context}]
              (if-let [match (r/match-by-path router (:uri request))]
                (let [method (:request-method request)
                      path-params (:path-params match)
                      endpoint (-> match :result method)
                      interceptors (or (:queue endpoint) (:interceptors endpoint))
                      request (-> request
                                  (impl/fast-assoc :path-params path-params)
                                  (impl/fast-assoc ::r/match match)
                                  (impl/fast-assoc ::r/router router))
                      context (assoc context :request request)
                      queue (interceptor/queue executor (concat default-interceptors interceptors))]
                  (interceptor/enqueue executor context queue))
                (interceptor/enqueue executor context default-queue)))
     :leave (fn [context]
              (if-not (:response context)
                (assoc context :response (default-handler (:request context)))
                context))}))

(defn ring-handler
  "Creates a ring-handler out of a http-router, optional default ring-handler
  and options map, with the following keys:

  | key             | description |
  | ----------------|-------------|
  | `:executor`     | `reitit.interceptor.Executor` for the interceptor chain
  | `:interceptors` | Optional sequence of interceptors that are always run before any other interceptors, even for the default handler"
  ([router opts]
    (ring-handler router nil opts))
  ([router default-handler {:keys [executor interceptors]}]
   (let [default-handler (or default-handler (fn ([_]) ([_ respond _] (respond nil))))
         default-queue (->> [default-handler]
                            (concat interceptors)
                            (map #(interceptor/into-interceptor % nil (r/options router)))
                            (interceptor/queue executor))
         router-opts (-> (r/options router)
                         (assoc ::interceptor/queue (partial interceptor/queue executor))
                         (dissoc :data) ; data is already merged into routes
                         (cond-> (seq interceptors)
                                 (update-in [:data :interceptors] (partial into (vec interceptors)))))
         router (reitit.http/router (r/routes router) router-opts)]
     (with-meta
       (fn
         ([request]
          (if-let [match (r/match-by-path router (:uri request))]
            (let [method (:request-method request)
                  path-params (:path-params match)
                  endpoint (-> match :result method)
                  interceptors (or (:queue endpoint) (:interceptors endpoint))
                  request (-> request
                              (impl/fast-assoc :path-params path-params)
                              (impl/fast-assoc ::r/match match)
                              (impl/fast-assoc ::r/router router))]
              (or (interceptor/execute executor interceptors request)
                  (interceptor/execute executor default-queue request)))
            (interceptor/execute executor default-queue (impl/fast-assoc request ::r/router router))))
         ([request respond raise]
          (let [default #(interceptor/execute executor default-queue % respond raise)]
            (if-let [match (r/match-by-path router (:uri request))]
              (let [method (:request-method request)
                    path-params (:path-params match)
                    endpoint (-> match :result method)
                    interceptors (or (:queue endpoint) (:interceptors endpoint))
                    request (-> request
                                (impl/fast-assoc :path-params path-params)
                                (impl/fast-assoc ::r/match match)
                                (impl/fast-assoc ::r/router router))
                    respond' (fn [response]
                               (if response
                                 (respond response)
                                 (default request)))]
                (if interceptors
                  (interceptor/execute executor interceptors request respond' raise)
                  (default request)))
              (default (impl/fast-assoc request ::r/router router))))
          nil))
       {::r/router router}))))

(defn get-router [handler]
  (-> handler meta ::r/router))

(defn get-match [request]
  (::r/match request))
