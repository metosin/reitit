(ns example.schema
  (:require [schema.core :as s]
            [reitit.ring.coercion-middleware :as coercion]
            [reitit.ring.coercion.schema :as schema-coercion]))

(def routes
  ["/schema"
   ["/plus" {:name ::plus
             :coercion schema-coercion/coercion
             :responses {200 {:schema {:total s/Int}}}
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
