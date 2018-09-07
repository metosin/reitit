(ns example.server
  (:require [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.http.coercion :as coercion]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            [reitit.interceptor.sieppari :as sieppari]
            [ring.adapter.jetty :as jetty]
            [muuntaja.core :as m]
            [clojure.java.io :as io]))

(def app
  (http/ring-handler
    (http/router
      [["/swagger.json"
        {:get {:no-doc true
               :swagger {:info {:title "my-api"
                                :description "with reitit-http"}}
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
                             ;; exception handling
                             (exception/exception-interceptor)
                             ;; decoding request body
                             (muuntaja/format-request-interceptor)
                             ;; coercing response bodys
                             (coercion/coerce-response-interceptor)
                             ;; coercing request parameters
                             (coercion/coerce-request-interceptor)
                             ;; multipart
                             (multipart/multipart-interceptor)]}})
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/"
         :config {:validatorUrl nil}})
      (ring/create-default-handler))
    {:executor sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))

(comment
  (start))
