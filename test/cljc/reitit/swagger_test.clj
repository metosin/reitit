(ns reitit.swagger-test
  (:require [clojure.test :refer :all]
            [reitit.ring :as ring]
            [reitit.swagger :as swagger]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec :as spec]
            [reitit.coercion.schema :as schema]
            [schema.core :refer [Int]]))

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
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

      {:data {:middleware [swagger/swagger-feature
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))

(deftest swagger-test
  (testing "endpoints work"
    (testing "spec"
      (is (= {:body {:total 3}, :status 200}
             (app
               {:request-method :get
                :uri "/api/spec/plus"
                :query-params {:x "2", :y "1"}}))))
    (testing "schema"
      (is (= {:body {:total 3}, :status 200}
             (app
               {:request-method :get
                :uri "/api/schema/plus"
                :query-params {:x "2", :y "1"}})))))
  (testing "swagger-spec"
    (let [spec (:body (app
                        {:request-method :get
                         :uri "/api/swagger.json"}))]
      (is (= {:x-id ::math
              :info {:title "my-api"}
              :paths {"/api/schema/plus" {:get {:parameters [{:description ""
                                                              :format "int32"
                                                              :in "query"
                                                              :name "x"
                                                              :required true
                                                              :type "integer"}
                                                             {:description ""
                                                              :format "int32"
                                                              :in "query"
                                                              :name "y"
                                                              :required true
                                                              :type "integer"}]
                                                :responses {200 {:description ""
                                                                 :schema {:additionalProperties false
                                                                          :properties {"total" {:format "int32"
                                                                                                :type "integer"}}
                                                                          :required ["total"]
                                                                          :type "object"}}}
                                                :summary "plus"}}
                      "/api/spec/plus" {:get {:parameters [{:description ""
                                                            :format "int64"
                                                            :in "query"
                                                            :name "x"
                                                            :required true
                                                            :type "integer"}
                                                           {:description ""
                                                            :format "int64"
                                                            :in "query"
                                                            :name "y"
                                                            :required true
                                                            :type "integer"}]
                                              :responses {200 {:description ""
                                                               :schema {:properties {"total" {:format "int64"
                                                                                              :type "integer"}}
                                                                        :required ["total"]
                                                                        :type "object"}}}
                                              :summary "plus"}}}}
             spec)))))
