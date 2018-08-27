(ns example.schema
  (:require [schema.core :as s]
            [reitit.coercion.schema]))

(def routes
  ["/schema" {:coercion reitit.coercion.schema/coercion}
   ["/plus" {:responses {200 {:body {:total s/Int}}}
             :get {:summary "plus with query-params"
                   :parameters {:query {:x s/Int, :y s/Int}}
                   :handler (fn [{{{:keys [x y]} :query} :parameters}]
                              {:status 200
                               :body {:total (+ x y)}})}
             :post {:summary "plus with body-params"
                    :parameters {:body {:x s/Int, :y s/Int}}
                    :handler (fn [{{{:keys [x y]} :body} :parameters}]
                               {:status 200
                                :body {:total (+ x y)}})}}]])
