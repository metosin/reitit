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
               :handler (swagger/create-swagger-handler)}}]

       ["/spec" {:coercion spec/coercion}
        ["/plus/:z"
         {:get {:summary "plus"
                :parameters {:query {:x int?, :y int?}
                             :path {:z int?}}
                :swagger {:responses {400 {:schema {:type "string"}
                                           :description "kosh"}}}
                :responses {200 {:body {:total int?}}
                            500 {:description "fail"}}
                :handler (fn [{{{:keys [x y]} :query
                                {:keys [z]} :path} :parameters}]
                           {:status 200, :body {:total (+ x y z)}})}}]]

       ["/schema" {:coercion schema/coercion}
        ["/plus/*z"
         {:get {:summary "plus"
                :parameters {:query {:x Int, :y Int}
                             :path {:z Int}}
                :swagger {:responses {400 {:schema {:type "string"}
                                           :description "kosh"}}}
                :responses {200 {:body {:total Int}}
                            500 {:description "fail"}}
                :handler (fn [{{{:keys [x y]} :query
                                {:keys [z]} :path} :parameters}]
                           {:status 200, :body {:total (+ x y z)}})}}]]]

      {:data {:middleware [swagger/swagger-feature
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))

(deftest swagger-test
  (testing "endpoints work"
    (testing "spec"
      (is (= {:body {:total 6}, :status 200}
             (app
               {:request-method :get
                :uri "/api/spec/plus/3"
                :query-params {:x "2", :y "1"}}))))
    (testing "schema"
      (is (= {:body {:total 6}, :status 200}
             (app
               {:request-method :get
                :uri "/api/schema/plus/3"
                :query-params {:x "2", :y "1"}})))))
  (testing "swagger-spec"
    (let [spec (:body (app
                        {:request-method :get
                         :uri "/api/swagger.json"}))]
      (is (= {:x-id #{::math}
              :swagger "2.0"
              :info {:title "my-api"}
              :paths {"/api/schema/plus/{z}" {:get {:parameters [{:description ""
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
                                                                  :type "integer"}
                                                                 {:in "path"
                                                                  :name "z"
                                                                  :description ""
                                                                  :type "integer"
                                                                  :required true
                                                                  :format "int32"}]
                                                    :responses {200 {:description ""
                                                                     :schema {:additionalProperties false
                                                                              :properties {"total" {:format "int32"
                                                                                                    :type "integer"}}
                                                                              :required ["total"]
                                                                              :type "object"}}
                                                                400 {:schema {:type "string"}
                                                                     :description "kosh"}
                                                                500 {:description "fail"}}
                                                    :summary "plus"}}
                      "/api/spec/plus/{z}" {:get {:parameters [{:description ""
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
                                                                :type "integer"}
                                                               {:in "path"
                                                                :name "z"
                                                                :description ""
                                                                :type "integer"
                                                                :required true
                                                                :format "int64"}]
                                                  :responses {200 {:description ""
                                                                   :schema {:properties {"total" {:format "int64"
                                                                                                  :type "integer"}}
                                                                            :required ["total"]
                                                                            :type "object"}}
                                                              400 {:schema {:type "string"}
                                                                   :description "kosh"}
                                                              500 {:description "fail"}}
                                                  :summary "plus"}}}}
             spec)))))

(deftest multiple-swagger-apis-test
  (let [ping-route ["/ping" {:get (constantly "ping")}]
        spec-route ["/swagger.json"
                    {:get {:no-doc true
                           :handler (swagger/create-swagger-handler)}}]
        app (ring/ring-handler
              (ring/router
                [["/common" {:swagger {:id #{::one ::two}}}
                  ping-route]

                 ["/one" {:swagger {:id ::one}}
                  ping-route
                  spec-route]

                 ["/two" {:swagger {:id ::two}}
                  ping-route
                  spec-route
                  ["/deep" {:swagger {:id ::one}}
                   ping-route]]
                 ["/one-two" {:swagger {:id #{::one ::two}}}
                  spec-route]]))
        spec-paths (fn [uri]
                     (-> {:request-method :get, :uri uri} app :body :paths keys))]
    (is (= ["/common/ping" "/one/ping" "/two/deep/ping"]
           (spec-paths "/one/swagger.json")))
    (is (= ["/common/ping" "/two/ping"]
           (spec-paths "/two/swagger.json")))
    (is (= ["/common/ping" "/one/ping" "/two/ping" "/two/deep/ping"]
           (spec-paths "/one-two/swagger.json")))))
