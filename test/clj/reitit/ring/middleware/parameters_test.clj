(ns reitit.ring.middleware.parameters-test
  (:require [clojure.test :refer [deftest is testing]]
            [reitit.ring :as ring]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]))

(deftest parameters-test
  (let [app (ring/ring-handler
             (ring/router
              ["/ping" {:get #(select-keys % [:params :query-params])}]
              {:data {:middleware [parameters/parameters-middleware]}}))]
    (is (= {:query-params {"kikka" "kukka"}
            :params {"kikka" "kukka"}}
           (app {:request-method :get
                 :uri "/ping"
                 :query-string "kikka=kukka"})))))

(deftest parameters-swagger-test
  (let [app (ring/ring-handler
             (ring/router
              [["/form-params" {:post {:parameters {:form {:x string?}}
                                       :handler identity}}]
               ["/body-params" {:post {:parameters {:body {:x string?}}
                                       :handler identity}}]
               ["/swagger.json" {:get {:no-doc true
                                       :handler (swagger/create-swagger-handler)}}]]
              {:data {:middleware [parameters/parameters-middleware]}}))
        spec (fn [path]
               (-> {:request-method :get :uri "/swagger.json"}
                   app
                   (get-in [:body :paths path :post])))]
    (testing "with form parameters"
      (is (= ["application/x-www-form-urlencoded"] (:consumes (spec "/form-params")))))
    (testing "with body parameters"
      (is (= nil (:consumes (spec "/body-params")))))))
