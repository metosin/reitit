(ns reitit.http.interceptors.parameters-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [reitit.http :as http]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.swagger :as swagger]))

(deftest parameters-test
  (let [app (http/ring-handler
             (http/router
              ["/ping" {:get #(select-keys % [:params :query-params])}]
              {:data {:interceptors [(parameters/parameters-interceptor)]}})
             {:executor sieppari/executor})]
    (is (= {:query-params {"kikka" "kukka"}
            :params {"kikka" "kukka"}}
           (app {:request-method :get
                 :uri "/ping"
                 :query-string "kikka=kukka"})))))

(deftest parameters-swagger-test
  (let [app (http/ring-handler
             (http/router
              [["/form-params" {:post {:parameters {:form {:x string?}}
                                       :handler identity}}]
               ["/body-params" {:post {:parameters {:body {:x string?}}
                                       :handler identity}}]
               ["/swagger.json" {:get {:no-doc true
                                       :handler (swagger/create-swagger-handler)}}]]
              {:data {:interceptors [(parameters/parameters-interceptor)]}})
             {:executor sieppari/executor})
        spec (fn [path]
               (-> {:request-method :get :uri "/swagger.json"}
                   app
                   (get-in [:body :paths path :post])))]
    (testing "with form parameters"
      (is (= ["application/x-www-form-urlencoded"] (:consumes (spec "/form-params")))))
    (testing "with body parameters"
      (is (= nil (:consumes (spec "/body-params")))))))
