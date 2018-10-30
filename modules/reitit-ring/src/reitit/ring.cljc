(ns reitit.ring
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.middleware :as middleware]
            [reitit.core :as r]
            [reitit.impl :as impl]
            #?@(:clj [[ring.util.mime-type :as mime-type]
                      [ring.util.response :as response]])
            [clojure.string :as str]))

(declare get-match)
(declare get-router)

(def http-methods #{:get :head :post :put :delete :connect :options :trace :patch})
(defrecord Methods [get head post put delete connect options trace patch])
(defrecord Endpoint [data handler path method middleware])

(defn ^:no-wiki group-keys [data]
  (reduce-kv
    (fn [[top childs] k v]
      (if (http-methods k)
        [top (assoc childs k v)]
        [(assoc top k v) childs])) [{} {}] data))

(defn coerce-handler [[path data] {:keys [expand] :as opts}]
  [path (reduce
          (fn [acc method]
            (if (contains? acc method)
              (update acc method expand opts)
              acc)) data http-methods)])

(defn compile-result [[path data] {:keys [::default-options-handler] :as opts}]
  (let [[top childs] (group-keys data)
        ->endpoint (fn [p d m s]
                     (-> (middleware/compile-result [p d] opts s)
                         (map->Endpoint)
                         (assoc :path p)
                         (assoc :method m)))
        ->methods (fn [any? data]
                    (reduce
                      (fn [acc method]
                        (cond-> acc
                                any? (assoc method (->endpoint path data method nil))))
                      (map->Methods
                        {:options
                         (if default-options-handler
                           (->endpoint path (assoc data
                                              :handler default-options-handler
                                              :no-doc true) :options nil))})
                      http-methods))]
    (if-not (seq childs)
      (->methods true top)
      (reduce-kv
        (fn [acc method data]
          (let [data (meta-merge top data)]
            (assoc acc method (->endpoint path data method method))))
        (->methods (:handler top) data)
        childs))))

(defn default-options-handler [request]
  (let [methods (->> request get-match :result (keep (fn [[k v]] (if v k))))
        allow (->> methods (map (comp str/upper-case name)) (str/join ","))]
    {:status 200, :body "", :headers {"Allow" allow}}))

;;
;; public api
;;

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for http-methods and Middleware. See [docs](https://metosin.github.io/reitit/)
  for details.

  Options:

  | key                                    | description |
  | ---------------------------------------|-------------|
  | `:reitit.middleware/transform`         | Function of `[Middleware] => [Middleware]` to transform the expanded Middleware (default: identity).
  | `:reitit.middleware/registry`          | Map of `keyword => IntoMiddleware` to replace keyword references into Middleware
  | `:reitit.ring/default-options-handler` | Default handler for `:options` method in endpoints (default: default-options-handler)

  Example:

      (router
        [\"/api\" {:middleware [wrap-format wrap-oauth2]}
          [\"/users\" {:get get-user
                       :post update-user
                       :delete {:middleware [wrap-delete]
                               :handler delete-user}}]])"
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (merge {:coerce coerce-handler
                      :compile compile-result
                      ::default-options-handler default-options-handler} opts)]
     (r/router data opts))))

(defn routes
  "Create a ring handler by combining several handlers into one."
  [& handlers]
  (let [single-arity (apply some-fn handlers)]
    (fn
      ([request]
       (single-arity request))
      ([request respond raise]
       (letfn [(f [handlers]
                 (if (seq handlers)
                   (let [handler (first handlers)
                         respond' #(if % (respond %) (f (rest handlers)))]
                     (handler request respond' raise))
                   (respond nil)))]
         (f handlers))))))

(defn redirect-trailing-slash-handler
  "A ring handler that redirects a missing path if there is an
  existing path that only differs in the ending slash.

  | key     | description |
  |---------|-------------|
  | :method | :add - redirects slash-less to slashed |
  |         | :strip - redirects slashed to slash-less |
  |         | :both - works both ways (default) |
  "
  ([] (redirect-trailing-slash-handler {:method :both}))
  ([{:keys [method]}]
   (let [redirect-handler (fn redirect-handler [request]
                            (let [uri (:uri request)
                                  status (if (= (:request-method request) :get) 301 308)
                                  maybe-redirect (fn maybe-redirect [path]
                                                   (if (r/match-by-path (::r/router request) path)
                                                     {:status status
                                                      :headers {"Location" path}
                                                      :body ""}))]
                              (if (str/ends-with? uri "/")
                                (if (not= method :add)
                                  (maybe-redirect (subs uri 0 (-> uri count dec))))
                                (if (not= method :strip)
                                  (maybe-redirect (str uri "/"))))))]
     (fn
       ([request]
        (redirect-handler request))
       ([request respond _]
        (respond (redirect-handler request)))))))

(defn create-default-handler
  "A default ring handler that can handle the following cases,
  configured via options:

  | key                    | description |
  | -----------------------|-------------|
  | `:not-found`           | 404, no routes matches
  | `:method-not-accepted` | 405, no method matches
  | `:not-acceptable`      | 406, handler returned `nil`"
  ([]
   (create-default-handler
     {:not-found (constantly {:status 404, :body "", :headers {}})
      :method-not-allowed (constantly {:status 405, :body "", :headers {}})
      :not-acceptable (constantly {:status 406, :body "", :headers {}})}))
  ([{:keys [not-found method-not-allowed not-acceptable]}]
   (fn
     ([request]
      (if-let [match (::r/match request)]
        (let [method (:request-method request :any)
              result (:result match)
              handler? (or (-> result method) (-> result :any))
              error-handler (if handler? not-acceptable method-not-allowed)]
          (error-handler request))
        (not-found request)))
     ([request respond _]
      (if-let [match (::r/match request)]
        (let [method (:request-method request :any)
              result (:result match)
              handler? (or (-> result method :handler) (-> result :any :handler))
              error-handler (if handler? not-acceptable method-not-allowed)]
          (respond (error-handler request)))
        (respond (not-found request)))))))

#?(:clj
   ;; TODO: optimize for perf
   ;; TODO: ring.middleware.not-modified/wrap-not-modified
   ;; TODO: ring.middleware.head/wrap-head
   ;; TODO: handle etags
   (defn create-resource-handler
     "A ring handler for serving classpath resources, configured via options:

     | key                | description |
     | -------------------|-------------|
     | :parameter         | optional name of the wildcard parameter, defaults to unnamed keyword `:`
     | :root              | optional resource root, defaults to `\"public\"`
     | :path              | optional path to mount the handler to. Works only if mounted outside of a router.
     | :loader            | optional class loader to resolve the resources
     | :index-files       | optional vector of index-files to look in a resource directory, defaults to `[\"index.html\"]`
     | :not-found-handler | optional handler function to use if the requested resource is missing (404 Not Found)"
     ([]
      (create-resource-handler nil))
     ([{:keys [parameter root path loader allow-symlinks? index-files paths not-found-handler]
        :or {parameter (keyword "")
             root "public"
             index-files ["index.html"]
             paths (constantly nil)
             not-found-handler (constantly {:status 404, :body "", :headers {}})}}]
      (let [options {:root root, :loader loader, :allow-symlinks? allow-symlinks?}
            path-size (count path)
            create (fn [handler]
                     (fn
                       ([request] (handler request))
                       ([request respond _] (respond (handler request)))))
            join-paths (fn [& paths]
                         (str/replace (str/replace (str/join "/" paths) #"([/]+)" "/") #"/$" ""))
            resource-response (fn [path]
                                (if-let [response (or (paths (join-paths "/" path))
                                                      (response/resource-response path options))]
                                  (response/content-type response (mime-type/ext-mime-type path))))
            path-or-index-response (fn [path uri]
                                     (or (resource-response path)
                                         (loop [[file & files] index-files]
                                           (if file
                                             (if (resource-response (join-paths path file))
                                               (response/redirect (join-paths uri file))
                                               (recur files))))))
            handler (if path
                      (fn [request]
                        (let [uri (:uri request)]
                          (if-let [path (if (>= (count uri) path-size) (subs uri path-size))]
                            (path-or-index-response path uri))))
                      (fn [request]
                        (let [uri (:uri request)
                              path (-> request :path-params parameter)]
                          (or (path-or-index-response path uri)
                              (not-found-handler request)))))]
        (create handler)))))

(defn ring-handler
  "Creates a ring-handler out of a router, optional default ring-handler
  and options map, with the following keys:

  | key           | description |
  | --------------|-------------|
  | `:middleware` | Optional sequence of middleware that wrap the ring-handler"
  ([router]
   (ring-handler router nil))
  ([router default-handler]
   (ring-handler router default-handler nil))
  ([router default-handler {:keys [middleware]}]
   (let [default-handler (or default-handler (fn ([_]) ([_ respond _] (respond nil))))
         wrap (if middleware (partial middleware/chain middleware) identity)]
     (with-meta
       (wrap
         (fn
           ([request]
            (if-let [match (r/match-by-path router (:uri request))]
              (let [method (:request-method request)
                    path-params (:path-params match)
                    result (:result match)
                    handler (-> result method :handler (or default-handler))
                    request (-> request
                                (impl/fast-assoc :path-params path-params)
                                (impl/fast-assoc ::r/match match)
                                (impl/fast-assoc ::r/router router))]
                (or (handler request) (default-handler request)))
              (default-handler (impl/fast-assoc request ::r/router router))))
           ([request respond raise]
            (if-let [match (r/match-by-path router (:uri request))]
              (let [method (:request-method request)
                    path-params (:path-params match)
                    result (:result match)
                    handler (-> result method :handler (or default-handler))
                    request (-> request
                                (impl/fast-assoc :path-params path-params)
                                (impl/fast-assoc ::r/match match)
                                (impl/fast-assoc ::r/router router))]
                ((routes handler default-handler) request respond raise))
              (default-handler (impl/fast-assoc request ::r/router router) respond raise))
            nil)))
       {::r/router router}))))

(defn get-router [handler]
  (-> handler meta ::r/router))

(defn get-match [request]
  (::r/match request))
