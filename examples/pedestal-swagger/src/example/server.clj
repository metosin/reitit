(ns example.server
  (:require [io.pedestal.http]
            [reitit.interceptor.pedestal :as pedestal]
            [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.http.coercion :as coercion]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            #_[reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            [muuntaja.core :as m]
            [clojure.java.io :as io]))

(def routing-interceptor
  (pedestal/routing-interceptor
    (http/router
      [["/swagger.json"
        {:get {:no-doc true
               :swagger {:info {:title "my-api"
                                :description "with pedestal & reitit-http"}}
               :handler (swagger/create-swagger-handler)}}]

       ["/files"
        {:swagger {:tags ["files"]}}

        ["/upload"
         {:post {:summary "upload a file"
                 :parameters {:multipart {:file multipart/temp-file-part}}
                 :responses {200 {:body {:name string?, :size int?}}}
                 :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                            {:status 200
                             :body {:name (:filename file)
                                    :size (:size file)}})}}]

        ["/download"
         {:get {:summary "downloads a file"
                :swagger {:produces ["image/png"]}
                :handler (fn [_]
                           {:status 200
                            :headers {"Content-Type" "image/png"}
                            :body (io/input-stream
                                    (io/resource "reitit.png"))})}}]]

       ["/math"
        {:swagger {:tags ["math"]}}

        ["/plus"
         {:get {:summary "plus with spec query parameters"
                :parameters {:query {:x int?, :y int?}}
                :responses {200 {:body {:total int?}}}
                :handler (fn [{{{:keys [x y]} :query} :parameters}]
                           {:status 200
                            :body {:total (+ x y)}})}
          :post {:summary "plus with spec body parameters"
                 :parameters {:body {:x int?, :y int?}}
                 :responses {200 {:body {:total int?}}}
                 :handler (fn [{{{:keys [x y]} :body} :parameters}]
                            {:status 200
                             :body {:total (+ x y)}})}}]]]

      {:data {:coercion spec-coercion/coercion
              :muuntaja m/instance
              :interceptors [;; query-params & form-params
                             (parameters/parameters-interceptor)
                             ;; content-negotiation
                             (muuntaja/format-negotiate-interceptor)
                             ;; encoding response body
                             (muuntaja/format-response-interceptor)
                             ;; exception handling - doesn't work
                             ;;(exception/exception-interceptor)
                             ;; decoding request body
                             (muuntaja/format-request-interceptor)
                             ;; coercing response bodys
                             (coercion/coerce-response-interceptor)
                             ;; coercing request parameters
                             (coercion/coerce-request-interceptor)
                             ;; multipart
                             (multipart/multipart-interceptor)]}})

    ;; optional default ring handler (if no routes have matched)
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/"
         :config {:validatorUrl nil}})
      (ring/create-default-handler))))

(defonce server (atom nil))

(defn start []
  (when @server
    (io.pedestal.http/stop @server)
    (println "server stopped"))
  (-> {:env :prod
       :io.pedestal.http/routes []
       :io.pedestal.http/resource-path "/public"
       :io.pedestal.http/type :jetty
       :io.pedestal.http/port 3000}
      (merge {:env :dev
              :io.pedestal.http/join? false
              :io.pedestal.http/allowed-origins {:creds true :allowed-origins (constantly true)}})
      (pedestal/default-interceptors routing-interceptor)
      io.pedestal.http/dev-interceptors
      io.pedestal.http/create-server
      io.pedestal.http/start
      (->> (reset! server)))
  (println "server running in port 3000"))

(comment
  (start))
