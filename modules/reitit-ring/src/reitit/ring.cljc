(ns reitit.ring
  (:require [clojure.string :as str]
            [meta-merge.core :refer [meta-merge]]
            #?@(:clj [[ring.util.mime-type :as mime-type]
             [ring.util.response :as response]])
            [reitit.core :as r]
            [reitit.exception :as ex]
            [reitit.impl :as impl]
            [reitit.middleware :as middleware]))

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

(defn compile-result [[path data] {:keys [::default-options-endpoint expand meta-merge-fn] :as opts}]
  (let [[top childs] (group-keys data)
        childs (cond-> childs
                 (and (not (:options childs)) (not (:handler top)) default-options-endpoint)
                 (assoc :options (expand default-options-endpoint opts)))
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
                     (map->Methods {})
                     http-methods))]
    (if-not (seq childs)
      (->methods true top)
      (reduce-kv
       (fn [acc method data]
         (let [data ((or meta-merge-fn meta-merge) top data)]
           (assoc acc method (->endpoint path data method method))))
       (->methods (:handler top) data)
       childs))))

(def default-options-handler
  (let [handler (fn [request]
                  (let [methods (->> request get-match :result (keep (fn [[k v]] (if v k))))
                        allow (->> methods (map (comp str/upper-case name)) (str/join ","))]
                    {:status 200, :body "", :headers {"Allow" allow}}))]
    (fn
      ([request]
       (handler request))
      ([request respond _]
       (respond (handler request))))))

(def default-options-endpoint
  {:no-doc true
   :handler default-options-handler})

;;
;; public api
;;

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for http-methods and Middleware. See documentation on [[reitit.core/router]] for
  available options. In addition, the following options are available:

  | key                                     | description
  | ----------------------------------------|-------------
  | `:reitit.middleware/transform`          | Function or vector of functions of type `[Middleware] => [Middleware]` to transform the expanded Middleware (default: identity)
  | `:reitit.middleware/registry`           | Map of `keyword => IntoMiddleware` to replace keyword references into Middleware
  | `:reitit.ring/default-options-endpoint` | Default endpoint for `:options` method in endpoints (default: default-options-endpoint)

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
                      ::default-options-endpoint default-options-endpoint}
                     opts)]
     (when (contains? opts ::default-options-handler)
       (ex/fail! (str "Option :reitit.ring/default-options-handler is deprecated."
                      " Use :reitit.ring/default-options-endpoint instead.")))
     (r/router data opts))))

(defn routes
  "Create a ring handler by combining several handlers into one."
  [& handlers]
  (if-let [single-arity (some->> handlers (keep identity) (seq) (apply some-fn))]
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
   (letfn [(maybe-redirect [{:keys [query-string] :as request} path]
             (if (and (seq path) (r/match-by-path (::r/router request) path))
               {:status (if (= (:request-method request) :get) 301 308)
                :headers {"Location" (if query-string (str path "?" query-string) path)}
                :body ""}))
           (redirect-handler [request]
             (let [uri (:uri request)]
               (if (str/ends-with? uri "/")
                 (if (not= method :add)
                   (maybe-redirect request (str/replace-first uri #"/+$" "")))
                 (if (not= method :strip)
                   (maybe-redirect request (str uri "/"))))))]
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
  | `:method-not-allowed`  | 405, no method matches
  | `:not-acceptable`      | 406, handler returned `nil`"
  ([]
   (create-default-handler {}))
  ([{:keys [not-found method-not-allowed not-acceptable]
     :or {not-found (constantly {:status 404, :body "", :headers {}})
          method-not-allowed (constantly {:status 405, :body "", :headers {}})
          not-acceptable (constantly {:status 406, :body "", :headers {}})}}]
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
   (defn -create-file-or-resource-handler
     [response-fn {:keys [parameter root path loader allow-symlinks? index-files paths not-found-handler]
                   :or {parameter (keyword "")
                        root "public"
                        index-files ["index.html"]
                        paths (constantly nil)
                        not-found-handler (if path
                                            (constantly nil)
                                            (constantly {:status 404, :body "", :headers {}}))}}]
     (let [options {:root root
                    :loader loader
                    :index-files? false
                    :allow-symlinks? allow-symlinks?}
           path-size (count path)
           create (fn [handler]
                    (fn
                      ([request] (handler request))
                      ([request respond _] (respond (handler request)))))
           join-paths (fn [& paths]
                        (str/replace (str/replace (str/join "/" paths) #"([/]+)" "/") #"/$" ""))
           response (fn [path]
                      (if-let [response (or (paths (join-paths "/" path))
                                            (response-fn path options))]
                        (response/content-type response (mime-type/ext-mime-type path))))
           path-or-index-response (fn [path uri]
                                    (or (response path)
                                        (loop [[file & files] index-files]
                                          (if file
                                            (if (response (join-paths path file))
                                              (response/redirect (join-paths uri file))
                                              (recur files))))))
           handler (if path
                     (fn [request]
                       (let [uri (impl/url-decode (:uri request))]
                         (if (str/starts-with? uri path)
                           (or (path-or-index-response (subs uri path-size) uri)
                               (not-found-handler request)))))
                     (fn [request]
                       (let [uri (impl/url-decode (:uri request))
                             path (-> request :path-params parameter)]
                         (or (path-or-index-response path uri)
                             (not-found-handler request)))))]
       (create handler))))

#?(:clj
   (defn create-resource-handler
     "A ring handler for serving classpath resources, configured via options:

     | key                | description |
     | -------------------|-------------|
     | :parameter         | optional name of the wildcard parameter, defaults to unnamed keyword `:`
     | :root              | optional resource root, defaults to `\"public\"`
     | :path              | path to mount the handler to. Required when mounted outside of a router, does not work inside a router.
     | :loader            | optional class loader to resolve the resources
     | :index-files       | optional vector of index-files to look in a resource directory, defaults to `[\"index.html\"]`
     | :not-found-handler | optional handler function to use if the requested resource is missing (404 Not Found)"
     ([]
      (create-resource-handler nil))
     ([opts]
      (-create-file-or-resource-handler response/resource-response opts))))

#?(:clj
   (defn create-file-handler
     "A ring handler for serving file resources, configured via options:

     | key                | description |
     | -------------------|-------------|
     | :parameter         | optional name of the wildcard parameter, defaults to unnamed keyword `:`
     | :root              | optional resource root, defaults to `\"public\"`
     | :path              | path to mount the handler to. Required when mounted outside of a router, does not work inside a router.
     | :loader            | optional class loader to resolve the resources
     | :index-files       | optional vector of index-files to look in a resource directory, defaults to `[\"index.html\"]`
     | :not-found-handler | optional handler function to use if the requested resource is missing (404 Not Found)"
     ([]
      (create-file-handler nil))
     ([opts]
      (-create-file-or-resource-handler response/file-response opts))))

(defn create-enrich-request [inject-match? inject-router?]
  (cond
    (and inject-match? inject-router?)
    (fn enrich-request [request path-params match router]
      (-> request
          (impl/fast-assoc :path-params path-params)
          (impl/fast-assoc ::r/match match)
          (impl/fast-assoc ::r/router router)))
    inject-router?
    (fn enrich-request [request path-params _ router]
      (-> request
          (impl/fast-assoc :path-params path-params)
          (impl/fast-assoc ::r/router router)))
    inject-match?
    (fn enrich-request [request path-params match _]
      (-> request
          (impl/fast-assoc :path-params path-params)
          (impl/fast-assoc ::r/match match)))
    :else
    (fn enrich-request [request path-params _ _]
      (-> request
          (impl/fast-assoc :path-params path-params)))))

(defn create-enrich-default-request [inject-router?]
  (if inject-router?
    (fn enrich-request [request router]
      (impl/fast-assoc request ::r/router router))
    (fn enrich-request [request _]
      request)))

(defn ring-handler
  "Creates a ring-handler out of a router, optional default ring-handler
  and options map, with the following keys:

  | key               | description |
  | ------------------|-------------|
  | `:middleware`     | Optional sequence of middleware that wrap the ring-handler
  | `:inject-match?`  | Boolean to inject `match` into request under `:reitit.core/match` key (default true)
  | `:inject-router?` | Boolean to inject `router` into request under `:reitit.core/router` key (default true)"
  ([router]
   (ring-handler router nil))
  ([router default-handler]
   (ring-handler router default-handler nil))
  ([router default-handler {:keys [middleware inject-match? inject-router?]
                            :or {inject-match? true, inject-router? true}}]
   (let [default-handler (or default-handler (fn ([_]) ([_ respond _] (respond nil))))
         wrap (if middleware (partial middleware/chain middleware) identity)
         enrich-request (create-enrich-request inject-match? inject-router?)
         enrich-default-request (create-enrich-default-request inject-router?)]
     (with-meta
       (wrap
        (fn
          ([request]
           (if-let [match (r/match-by-path router (:uri request))]
             (let [method (:request-method request)
                   path-params (:path-params match)
                   result (:result match)
                   handler (-> result method :handler (or default-handler))
                   request (enrich-request request path-params match router)]
               (or (handler request) (default-handler request)))
             (default-handler (enrich-default-request request router))))
          ([request respond raise]
           (if-let [match (r/match-by-path router (:uri request))]
             (let [method (:request-method request)
                   path-params (:path-params match)
                   result (:result match)
                   handler (-> result method :handler (or default-handler))
                   request (enrich-request request path-params match router)]
               ((routes handler default-handler) request respond raise))
             (default-handler (enrich-default-request request router) respond raise))
           nil)))
       {::r/router router}))))

(defn reloading-ring-handler
  "Returns a ring-handler that recreates the actual ring-handler for each request.
  Takes a 0-arity function that should return a valid ring-handler. Effectively creates
  an auto-reloading ring-handler, which is good for REPL-driven development.

  Example:

        ;; for dev-mode, recreate the ring-handler for each request, for prod, just once
        (let [dev-mode ...
              f (fn [] (reitit.ring/ring-handler ...)]
          (if dev-mode (reitit.ring/reloading-ring-handler f) (f)))"
  [f]
  (fn
    ([request] ((f) request))
    ([request respond raise] ((f) request respond raise))))

(defn get-router [handler]
  (-> handler meta ::r/router))

(defn get-match [request]
  (::r/match request))
