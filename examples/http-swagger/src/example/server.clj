(ns example.server
  (:require [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.http.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            ;; Uncomment to use
            ; [reitit.http.interceptors.dev :as dev]
            ; [reitit.http.spec :as spec]
            ; [spec-tools.spell :as spell]
            [ring.adapter.jetty :as jetty]
            [aleph.http :as aleph]
            [muuntaja.core :as m]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [sieppari.async.manifold]
            [manifold.deferred :as d]))

(s/def ::x int?)
(s/def ::y int?)
(s/def ::total pos-int?)

(s/def ::seed string?)
(s/def ::results
  (st/spec
    {:spec (s/and int? #(< 0 % 100))
     :description "between 1-100"
     :swagger/default 10
     :reason "invalid number"}))

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

       ["/async"
        {:get {:swagger {:tags ["async"]}
               :summary "fetches random users asynchronously over the internet"
               :parameters {:query (s/keys :req-un [::results] :opt-un [::seed])}
               :responses {200 {:body any?}}
               :handler (fn [{{{:keys [seed results]} :query} :parameters}]
                          (d/chain
                            (aleph/get
                              "https://randomuser.me/api/"
                              {:query-params {:seed seed, :results results}})
                            :body
                            (partial m/decode "application/json")
                            :results
                            (fn [results]
                              {:status 200
                               :body results})))}}]

       ["/math"
        {:swagger {:tags ["math"]}}

        ["/plus"
         {:get {:summary "plus with data-spec query parameters"
                :parameters {:query {:x int?, :y int?}}
                :responses {200 {:body {:total pos-int?}}}
                :handler (fn [{{{:keys [x y]} :query} :parameters}]
                           {:status 200
                            :body {:total (+ x y)}})}
          :post {:summary "plus with data-spec body parameters"
                 :parameters {:body {:x int?, :y int?}}
                 :responses {200 {:body {:total int?}}}
                 :handler (fn [{{{:keys [x y]} :body} :parameters}]
                            {:status 200
                             :body {:total (+ x y)}})}}]

        ["/minus"
         {:get {:summary "minus with clojure.spec query parameters"
                :parameters {:query (s/keys :req-un [::x ::y])}
                :responses {200 {:body (s/keys :req-un [::total])}}
                :handler (fn [{{{:keys [x y]} :query} :parameters}]
                           {:status 200
                            :body {:total (- x y)}})}
          :post {:summary "minus with clojure.spec body parameters"
                 :parameters {:body (s/keys :req-un [::x ::y])}
                 :responses {200 {:body (s/keys :req-un [::total])}}
                 :handler (fn [{{{:keys [x y]} :body} :parameters}]
                            {:status 200
                             :body {:total (- x y)}})}}]]]

      {;:reitit.interceptor/transform dev/print-context-diffs ;; pretty context diffs
       ;;:validate spec/validate ;; enable spec validation for route data
       ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
       :exception pretty/exception
       :data {:coercion reitit.coercion.spec/coercion
              :muuntaja m/instance
              :interceptors [;; swagger feature
                             swagger/swagger-feature
                             ;; query-params & form-params
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
         :config {:validatorUrl nil
                  :operationsSorter "alpha"}})
      (ring/create-default-handler))
    {:executor sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false, :async true})
  ;(aleph/start-server (aleph/wrap-ring-async-handler #'app) {:port 3000})
  (println "server running in port 3000"))

(comment
  (start))
