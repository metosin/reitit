(ns reitit.http
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.interceptor :as interceptor]
            [reitit.ring :as ring]
            [reitit.core :as r]
            [reitit.impl :as impl]))

(defrecord Endpoint [data handler path method interceptors queue])

(defprotocol Executor
  (queue
    [this interceptors]
    "takes a sequence of interceptors and compiles them to queue for the executor")
  (execute
    [this request interceptors]
    [this request interceptors respond raise]
    "executes the interceptor chain"))

(defn coerce-handler [[path data] {:keys [expand] :as opts}]
  [path (reduce
          (fn [acc method]
            (if (contains? acc method)
              (update acc method expand opts)
              acc)) data ring/http-methods)])

(defn compile-result [[path data] {:keys [::queue] :as opts}]
  (let [[top childs] (ring/group-keys data)
        ->handler (fn [handler]
                    (if handler
                      (fn [ctx]
                        (->> ctx :request handler (assoc ctx :response)))))
        compile (fn [[path data] opts scope]
                  (let [data (update data :handler ->handler)]
                    (interceptor/compile-result [path data] opts scope)))
        ->endpoint (fn [p d m s]
                     (let [compiled (compile [p d] opts s)
                           interceptors (:interceptors compiled)]
                       (-> compiled
                           (map->Endpoint)
                           (assoc :path p)
                           (assoc :method m)
                           (assoc :queue ((or queue identity) interceptors)))))
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
  support for http-methods and Interceptors. See [docs](https://metosin.github.io/reitit/)
  for details.

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
   (let [opts (meta-merge {:coerce coerce-handler, :compile compile-result} opts)]
     (r/router data opts))))

(defn ring-handler
  "Creates a ring-handler out of a http-router,
  a default ring-handler and options map, with the following keys:

  | key             | description |
  | ----------------|-------------|
  | `:executor`     | [[Executor]] for the interceptor chain
  | `:interceptors` | Optional sequence of interceptors that are always run before any other interceptors, even for the default handler"
  [router default-handler {:keys [executor interceptors]}]
  (let [default-handler (or default-handler (fn ([_]) ([_ respond _] (respond nil))))
        default-queue (queue executor (interceptor/into-interceptor (concat interceptors [default-handler]) nil (r/options router)))
        router-opts (-> (r/options router)
                        (assoc ::queue (partial queue executor))
                        (update :interceptors (partial concat interceptors)))
        router (router (r/routes router) router-opts)]
    (with-meta
      (fn
        ([request]
         (if-let [match (r/match-by-path router (:uri request))]
           (let [method (:request-method request)
                 path-params (:path-params match)
                 result (:result match)
                 interceptors (-> result method :interceptors)
                 request (-> request
                             (impl/fast-assoc :path-params path-params)
                             (impl/fast-assoc ::r/match match)
                             (impl/fast-assoc ::r/router router))]
             (execute executor interceptors request))
           (execute executor default-queue request)))
        ([request respond raise]
         (if-let [match (r/match-by-path router (:uri request))]
           (let [method (:request-method request)
                 path-params (:path-params match)
                 result (:result match)
                 interceptors (-> result method :interceptors)
                 request (-> request
                             (impl/fast-assoc :path-params path-params)
                             (impl/fast-assoc ::r/match match)
                             (impl/fast-assoc ::r/router router))]
             (execute executor interceptors request respond raise))
           (execute executor default-queue request respond raise))
         nil))
      {::r/router router})))

(defn get-router [handler]
  (-> handler meta ::r/router))

(defn get-match [request]
  (::r/match request))
