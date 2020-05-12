(ns reitit.http
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.interceptor :as interceptor]
            [reitit.exception :as ex]
            [reitit.ring :as ring]
            [reitit.core :as r]))

(defrecord Endpoint [data interceptors queue handler path method])

(defn coerce-handler [[path data] {:keys [expand] :as opts}]
  [path (reduce
          (fn [acc method]
            (if (contains? acc method)
              (update acc method expand opts)
              acc)) data ring/http-methods)])

(defn compile-result [[path data] {:keys [::default-options-endpoint expand] :as opts}]
  (let [[top childs] (ring/group-keys data)
        childs (cond-> childs
                       (and (not (:options childs)) (not (:handler top)) default-options-endpoint)
                       (assoc :options (expand default-options-endpoint opts)))
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
                      (ring/map->Methods {})
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
  support for http-methods and Interceptors. See documentation on [[reitit.core/router]]
  for available options. In addition, the following options are available:

  | key                                     | description
  | ----------------------------------------|-------------
  | `:reitit.interceptor/transform`         | Function or vector of functions of type `[Interceptor] => [Interceptor]` to transform the expanded Interceptors (default: identity)
  | `:reitit.interceptor/registry`          | Map of `keyword => IntoInterceptor` to replace keyword references into Interceptors
  | `:reitit.http/default-options-endpoint` | Default endpoint for `:options` method in endpoints (default: reitit.ring/default-options-endpoint)

  Example:

      (router
        [\"/api\" {:interceptors [format-i oauth2-i]}
          [\"/users\" {:get get-user
                       :post update-user
                       :delete {:interceptors [delete-i]
                               :handler delete-user}}]])"
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (merge {:coerce coerce-handler
                      :compile compile-result
                      ::default-options-endpoint ring/default-options-endpoint} opts)]
     (when (contains? opts ::default-options-handler)
       (ex/fail! (str "Option :reitit.http/default-options-handler is deprecated."
                      " Use :reitit.http/default-options-endpoint instead.")))
     (r/router data opts))))

(defn routing-interceptor
  "Creates a Pedestal-style routing interceptor that enqueues the interceptors into context.
  Takes http-router, default ring-handler and and options map, with the following keys:

  | key               | description |
  | ------------------|-------------|
  | `:executor`       | `reitit.interceptor.Executor` for the interceptor chain
  | `:interceptors`   | Optional sequence of interceptors that are always run before any other interceptors, even for the default handler
  | `:inject-match?`  | Boolean to inject `match` into request under `:reitit.core/match` key (default true)
  | `:inject-router?` | Boolean to inject `router` into request under `:reitit.core/router` key (default true)"
  [router default-handler {:keys [interceptors executor inject-match? inject-router?]
                           :or {inject-match? true, inject-router? true}}]
  (let [default-handler (or default-handler (fn ([_])))
        default-interceptors (->> interceptors
                                  (map #(interceptor/into-interceptor % nil (r/options router))))
        default-queue (interceptor/queue executor default-interceptors)
        enrich-request (ring/create-enrich-request inject-match? inject-router?)]
    {:name ::router
     :enter (fn [{:keys [request] :as context}]
              (if-let [match (r/match-by-path router (:uri request))]
                (let [method (:request-method request)
                      path-params (:path-params match)
                      endpoint (-> match :result method)
                      interceptors (or (:queue endpoint) (:interceptors endpoint))
                      request (enrich-request request path-params match router)
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

  | key               | description |
  | ------------------|-------------|
  | `:executor`       | `reitit.interceptor.Executor` for the interceptor chain
  | `:interceptors`   | Optional sequence of interceptors that are always run before any other interceptors, even for the default handler
  | `:inject-match?`  | Boolean to inject `match` into request under `:reitit.core/match` key (default true)
  | `:inject-router?` | Boolean to inject `router` into request under `:reitit.core/router` key (default true)"
  ([router opts]
   (ring-handler router nil opts))
  ([router default-handler {:keys [executor interceptors inject-match? inject-router?]
                            :or {inject-match? true, inject-router? true}}]
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
         router (reitit.http/router (r/routes router) router-opts) ;; will re-compile the interceptors
         enrich-request (ring/create-enrich-request inject-match? inject-router?)
         enrich-default-request (ring/create-enrich-default-request inject-router?)]
     (with-meta
       (fn
         ([request]
          (if-let [match (r/match-by-path router (:uri request))]
            (let [method (:request-method request)
                  path-params (:path-params match)
                  endpoint (-> match :result method)
                  interceptors (or (:queue endpoint) (:interceptors endpoint))
                  request (enrich-request request path-params match router)]
              (or (interceptor/execute executor interceptors request)
                  (interceptor/execute executor default-queue request)))
            (interceptor/execute executor default-queue (enrich-default-request request router))))
         ([request respond raise]
          (let [default #(interceptor/execute executor default-queue % respond raise)]
            (if-let [match (r/match-by-path router (:uri request))]
              (let [method (:request-method request)
                    path-params (:path-params match)
                    endpoint (-> match :result method)
                    interceptors (or (:queue endpoint) (:interceptors endpoint))
                    request (enrich-request request path-params match router)
                    respond' (fn [response]
                               (if response
                                 (respond response)
                                 (default request)))]
                (if interceptors
                  (interceptor/execute executor interceptors request respond' raise)
                  (default request)))
              (default (enrich-default-request request router))))
          nil))
       {::r/router router}))))

(defn get-router [handler]
  (-> handler meta ::r/router))

(defn get-match [request]
  (::r/match request))
