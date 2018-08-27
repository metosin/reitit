(ns example.dspec
  (:require [reitit.coercion.spec]))

(def routes
  ["/dspec" {:coercion reitit.coercion.spec/coercion}
   ["/plus" {:responses {200 {:body {:total int?}}}
             :get {:summary "plus with query-params"
                   :parameters {:query {:x int?, :y int?}}
                   :handler (fn [{{{:keys [x y]} :query} :parameters}]
                              {:status 200
                               :body {:total (+ x y)}})}
             :post {:summary "plus with body-params"
                    :parameters {:body {:x int?, :y int?}}
                    :handler (fn [{{{:keys [x y]} :body} :parameters}]
                               {:status 200
                                :body {:total (+ x y)}})}}]])
