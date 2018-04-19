(ns example.server
  (:require [reitit.ring :as ring]
            [reitit.swagger :as swagger]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec :as spec]
            [reitit.coercion.schema :as schema]
            [schema.core :refer [Int]]

            [ring.adapter.jetty :as jetty]
            [ring.middleware.params]
            [muuntaja.middleware]))

(def app
  (ring/ring-handler
    (ring/router
      [["/api"
        {:swagger {:id ::math}}

        ["/swagger.json"
         {:get {:no-doc true
                :swagger {:info {:title "my-api"}}
                :handler swagger/swagger-spec-handler}}]

        ["/spec" {:coercion spec/coercion}
         ["/plus"
          {:get {:summary "plus"
                 :parameters {:query {:x int?, :y int?}}
                 :responses {200 {:body {:total int?}}}
                 :handler (fn [{{{:keys [x y]} :query} :parameters}]
                            {:status 200, :body {:total (+ x y)}})}}]]

        ["/schema" {:coercion schema/coercion}
         ["/plus"
          {:get {:summary "plus"
                 :parameters {:query {:x Int, :y Int}}
                 :responses {200 {:body {:total Int}}}
                 :handler (fn [{{{:keys [x y]} :query} :parameters}]
                            {:status 200, :body {:total (+ x y)}})}}]]]

       ;; serve api-docs here
       ["/api-docs/*"
        (constantly {:status 200, :body "api-docs"})]]

      {:data {:middleware [ring.middleware.params/wrap-params
                           muuntaja.middleware/wrap-format
                           swagger/swagger-feature
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})
    (ring/create-default-handler)))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))

(comment
  (start))
