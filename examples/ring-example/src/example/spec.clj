(ns example.spec
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [reitit.coercion.spec :as spec-coercion]))

;; wrap into Spec Records to enable runtime conforming
(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::total spec/int?)

(def routes
  ["/spec" {:coercion spec-coercion/coercion}
   ["/plus" {:name ::plus
             :responses {200 {:schema (s/keys :req-un [::total])}}
             :get {:summary "plus with query-params"
                   :parameters {:query (s/keys :req-un [::x ::y])}
                   :handler (fn [{{{:keys [x y]} :query} :parameters}]
                              {:status 200
                               :body {:total (+ x y)}})}
             :post {:summary "plus with body-params"
                    :parameters {:body (s/keys :req-un [::x ::y])}
                    :handler (fn [{{{:keys [x y]} :body} :parameters}]
                               {:status 200
                                :body {:total (+ x y)}})}}]])
