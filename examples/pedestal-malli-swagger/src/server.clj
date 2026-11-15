(ns server
  (:require [clojure.java.io :as io]
            [io.pedestal.http.route]
            [reitit.interceptor]
            [reitit.dev.pretty :as pretty]
            [reitit.coercion.malli]
            [io.pedestal.http]
            [reitit.ring]
            [reitit.ring.malli]
            [reitit.http]
            [reitit.pedestal]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.multipart :as multipart]
            [muuntaja.core]
            [malli.util :as mu]))

(defn reitit-routes
  [_config]
  [["/swagger.json" {:get {:no-doc  true
                           :swagger {:info {:title       "my-api"
                                            :description "with [malli](https://github.com/metosin/malli) and reitit-ring"}
                                     :tags [{:name        "files",
                                             :description "file api"}
                                            {:name        "math",
                                             :description "math api"}]}
                           :handler (swagger/create-swagger-handler)}}]
   ["/files" {:swagger {:tags ["files"]}}
    ["/upload"
     {:post {:summary    "upload a file"
             :parameters {:multipart [:map [:file reitit.ring.malli/temp-file-part]]}
             :responses  {200 {:body [:map
                                      [:name string?]
                                      [:size int?]]}}
             :handler    (fn [{{{{:keys [filename
                                         size]} :file}
                                :multipart}
                               :parameters}]
                           {:status 200
                            :body   {:name filename
                                     :size size}})}}]
    ["/download" {:get {:summary "downloads a file"
                        :swagger {:produces ["image/png"]}
                        :handler (fn [_]
                                   {:status  200
                                    :headers {"Content-Type" "image/png"}
                                    :body    (-> "reitit.png"
                                                 (io/resource)
                                                 (io/input-stream))})}}]]
   ["/math" {:swagger {:tags ["math"]}}
    ["/plus"
     {:get  {:summary    "plus with malli query parameters"
             :parameters {:query [:map
                                  [:x
                                   {:title               "X parameter"
                                    :description         "Description for X parameter"
                                    :json-schema/default 42}
                                   int?]
                                  [:y int?]]}
             :responses  {200 {:body [:map [:total int?]]}}
             :handler    (fn [{{{:keys [x
                                        y]}
                                :query}
                               :parameters}]
                           {:status 200
                            :body   {:total (+ x y)}})}
      :post {:summary    "plus with malli body parameters"
             :parameters {:body [:map
                                 [:x
                                  {:title               "X parameter"
                                   :description         "Description for X parameter"
                                   :json-schema/default 42}
                                  int?]
                                 [:y int?]]}
             :responses  {200 {:body [:map [:total int?]]}}
             :handler    (fn [{{{:keys [x
                                        y]}
                                :body}
                               :parameters}]
                           {:status 200
                            :body   {:total (+ x y)}})}}]]])

(defn reitit-ring-routes
  [_config]
  [(swagger-ui/create-swagger-ui-handler
    {:path   "/"
     :config {:validatorUrl     nil
              :operationsSorter "alpha"}})
   (reitit.ring/create-resource-handler)
   (reitit.ring/create-default-handler)])


(defn reitit-router-config
  [_config]
  {:exception pretty/exception
   :data      {:coercion     (reitit.coercion.malli/create
                              {:error-keys       #{:coercion
                                                   :in
                                                   :schema
                                                   :value
                                                   :errors
                                                   :humanized}
                               :compile          mu/closed-schema
                               :strip-extra-keys true
                               :default-values   true
                               :options          nil})
               :muuntaja     muuntaja.core/instance
               :interceptors [swagger/swagger-feature
                              (parameters/parameters-interceptor)
                              (muuntaja/format-negotiate-interceptor)
                              (muuntaja/format-response-interceptor)
                              (muuntaja/format-request-interceptor)
                              (coercion/coerce-response-interceptor)
                              (coercion/coerce-request-interceptor)
                              (multipart/multipart-interceptor)]}})

(def config
  {:env                             :dev
   :io.pedestal.http/routes         []
   :io.pedestal.http/type           :jetty
   :io.pedestal.http/port           3000
   :io.pedestal.http/join?          false
   :io.pedestal.http/secure-headers {:content-security-policy-settings
                                     {:default-src "'self'"
                                      :style-src   "'self' 'unsafe-inline'"
                                      :script-src  "'self' 'unsafe-inline'"}}
   ::reitit-routes reitit-routes
   ::reitit-ring-routes reitit-ring-routes
   ::reitit-router-config reitit-router-config})

(defn reitit-http-router
  [{::keys [reitit-routes
            reitit-ring-routes
            reitit-router-config]
    :as    config}]
  (reitit.pedestal/routing-interceptor
   (reitit.http/router
    (reitit-routes config)
    (reitit-router-config config))
   (->> config
        reitit-ring-routes
        (apply reitit.ring/routes))))

(defonce server (atom nil))

(defn start
  [server
   config]
  (when @server
    (io.pedestal.http/stop @server)
    (println "server stopped"))
  (-> config
      io.pedestal.http/default-interceptors
      (reitit.pedestal/replace-last-interceptor (reitit-http-router config))
      io.pedestal.http/dev-interceptors
      io.pedestal.http/create-server
      io.pedestal.http/start
      (->> (reset! server)))
  (println "server running in port 3000"))

#_(start server config)
