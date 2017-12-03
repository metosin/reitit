(ns reitit.ring
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.ring.middleware :as middleware]
            [reitit.core :as r]
            [reitit.impl :as impl]))

(def http-methods #{:get :head :patch :delete :options :post :put})
(defrecord Methods [get head post put delete trace options connect patch any])

(defn- group-keys [data]
  (reduce-kv
    (fn [[top childs] k v]
      (if (http-methods k)
        [top (assoc childs k v)]
        [(assoc top k v) childs])) [{} {}] data))

(defn ring-handler
  "Creates a ring-handler out of a ring-router.
  Supports both 1 (sync) and 3 (async) arities."
  [router]
  (let [default-handler (constantly nil)]
    (with-meta
      (fn
        ([request]
         (if-let [match (r/match-by-path router (:uri request))]
           (let [method (:request-method request :any)
                 params (:params match)
                 result (:result match)
                 handler (or (-> result method :handler)
                             (-> result :any (:handler default-handler)))
                 request (cond-> (impl/fast-assoc request ::match match)
                                 (seq params) (impl/fast-assoc :path-params params))]
             (handler request))))
        ([request respond raise]
         (if-let [match (r/match-by-path router (:uri request))]
           (let [method (:request-method request :any)
                 params (:params match)
                 result (:result match)
                 handler (or (-> result method :handler)
                             (-> result :any (:handler default-handler)))
                 request (cond-> (impl/fast-assoc request ::match match)
                                 (seq params) (impl/fast-assoc :path-params params))]
             (handler request respond raise))
           (respond nil))))
      {::router router})))

(defn get-router [handler]
  (some-> handler meta ::router))

(defn get-match [request]
  (::match request))

(defn coerce-handler [[path data] {:keys [expand] :as opts}]
  [path (reduce
          (fn [acc method]
            (if (contains? acc method)
              (update acc method expand opts)
              acc)) data http-methods)])

(defn compile-result [[path data] opts]
  (let [[top childs] (group-keys data)]
    (if-not (seq childs)
      (map->Methods {:any (middleware/compile-result [path top] opts)})
      (reduce-kv
        (fn [acc method data]
          (let [data (meta-merge top data)]
            (assoc acc method (middleware/compile-result [path data] opts method))))
        (map->Methods {:any (if (:handler top) (middleware/compile-result [path data] opts))})
        childs))))

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for http-methods and Middleware. See [docs](https://metosin.github.io/reitit/)
  for details.

  Example:

      (router
        [\"/api\" {:middleware [wrap-format wrap-oauth2]}
          [\"/users\" {:get get-user
                       :post update-user
                       :delete {:middleware [wrap-delete]
                               :handler delete-user}}]])

  See router options from [[reitit.core/router]] and [[reitit.ring.middleware/router]]."
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:coerce coerce-handler, :compile compile-result} opts)]
     (r/router data opts))))
