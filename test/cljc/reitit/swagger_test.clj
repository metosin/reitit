(ns reitit.swagger-test
  (:require [clojure.test :refer :all]
            [reitit.ring :as ring]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec :as spec]
            [reitit.coercion.malli :as malli]
            [reitit.coercion.schema :as schema]
            [schema.core :refer [Int]]
            [muuntaja.core :as m]
            [spec-tools.data-spec :as ds]))

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
                           {:status 200, :body {:total (+ x y z)}})}
          :post {:summary "plus with body"
                 :parameters {:body (ds/maybe [int?])
                              :path {:z int?}}
                 :swagger {:responses {400 {:schema {:type "string"}
                                            :description "kosh"}}}
                 :responses {200 {:body {:total int?}}
                             500 {:description "fail"}}
                 :handler (fn [{{{:keys [z]} :path
                                 xs :body} :parameters}]
                            {:status 200, :body {:total (+ (reduce + xs) z)}})}}]]

       ["/malli" {:coercion malli/coercion}
        ["/plus/*z"
         {:get {:summary "plus"
                :parameters {:query [:map [:x int?] [:y int?]]
                             :path [:map [:z int?]]}
                :swagger {:responses {400 {:schema {:type "string"}
                                           :description "kosh"}}}
                :responses {200 {:body [:map [:total int?]]}
                            500 {:description "fail"}}
                :handler (fn [{{{:keys [x y]} :query
                                {:keys [z]} :path} :parameters}]
                           {:status 200, :body {:total (+ x y z)}})}
          :post {:summary "plus with body"
                 :parameters {:body [:maybe [:vector int?]]
                              :path [:map [:z int?]]}
                 :swagger {:responses {400 {:schema {:type "string"}
                                            :description "kosh"}}}
                 :responses {200 {:body [:map [:total int?]]}
                             500 {:description "fail"}}
                 :handler (fn [{{{:keys [z]} :path
                                 xs :body} :parameters}]
                            {:status 200, :body {:total (+ (reduce + xs) z)}})}}]]

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
                :query-params {:x "2", :y "1"}})))
      (is (= {:body {:total 7}, :status 200}
             (app
               {:request-method :post
                :uri "/api/spec/plus/3"
                :body-params [1 3]}))))
    (testing "schema"
      (is (= {:body {:total 6}, :status 200}
             (app
               {:request-method :get
                :uri "/api/schema/plus/3"
                :query-params {:x "2", :y "1"}})))))
  (testing "swagger-spec"
    (let [spec (:body (app
                        {:request-method :get
                         :uri "/api/swagger.json"}))
          expected {:x-id #{::math}
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
                            "/api/malli/plus/{z}" {:get {:parameters [{:description ""
                                                                       :format "int64"
                                                                       :in "query"
                                                                       :name :x
                                                                       :required true
                                                                       :type "integer"}
                                                                      {:description ""
                                                                       :format "int64"
                                                                       :in "query"
                                                                       :name :y
                                                                       :required true
                                                                       :type "integer"}
                                                                      {:in "path"
                                                                       :name :z
                                                                       :description ""
                                                                       :type "integer"
                                                                       :required true
                                                                       :format "int64"}]
                                                         :responses {200 {:description ""
                                                                          :schema {:properties {:total {:format "int64"
                                                                                                        :type "integer"}}
                                                                                   :required [:total]
                                                                                   :type "object"}}
                                                                     400 {:schema {:type "string"}
                                                                          :description "kosh"}
                                                                     500 {:description "fail"}}
                                                         :summary "plus"}
                                                   :post {:parameters [{:in "body",
                                                                        :name "",
                                                                        :description "",
                                                                        :required false,
                                                                        :schema {:type "array",
                                                                                 :items {:type "integer",
                                                                                         :format "int64"}
                                                                                 :x-nullable true}}
                                                                       {:in "path"
                                                                        :name :z
                                                                        :description ""
                                                                        :type "integer"
                                                                        :required true
                                                                        :format "int64"}]
                                                          :responses {200 {:description ""
                                                                           :schema {:properties {:total {:format "int64"
                                                                                                         :type "integer"}}
                                                                                    :required [:total]
                                                                                    :type "object"}}
                                                                      400 {:schema {:type "string"}
                                                                           :description "kosh"}
                                                                      500 {:description "fail"}}
                                                          :summary "plus with body"}}
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
                                                        :summary "plus"}
                                                  :post {:parameters [{:in "body",
                                                                       :name "",
                                                                       :description "",
                                                                       :required false,
                                                                       :schema {:type "array",
                                                                                :items {:type "integer",
                                                                                        :format "int64"}
                                                                                :x-nullable true}}
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
                                                         :summary "plus with body"}}}}]
      (is (= expected spec))

      (testing "ring-async swagger-spec"
        (let [response* (atom nil)
              respond (partial reset! response*)]
          (app {:request-method :get
                :uri "/api/swagger.json"} respond (fn [_] (is false)))
          (is (= expected (:body @response*))))))))

(defn spec-paths [app uri]
  (-> {:request-method :get, :uri uri} app :body :paths keys))

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
                  spec-route]]))]
    (is (= ["/common/ping" "/one/ping" "/two/deep/ping"]
           (spec-paths app "/one/swagger.json")))
    (is (= ["/common/ping" "/two/ping"]
           (spec-paths app "/two/swagger.json")))
    (is (= ["/common/ping" "/one/ping" "/two/ping" "/two/deep/ping"]
           (spec-paths app "/one-two/swagger.json")))))

(deftest swagger-ui-congif-test
  (let [app (swagger-ui/create-swagger-ui-handler
              {:path "/"
               :config {:jsonEditor true}})]
    (is (= 302 (:status (app {:request-method :get, :uri "/"}))))
    (is (= 200 (:status (app {:request-method :get, :uri "/index.html"}))))
    (is (= {:jsonEditor true, :url "/swagger.json"}
           (->> {:request-method :get, :uri "/config.json"}
                (app) :body (m/decode m/instance "application/json"))))))

(deftest without-swagger-id-test
  (let [app (ring/ring-handler
              (ring/router
                [["/ping"
                  {:get (constantly "ping")}]
                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]))]
    (is (= ["/ping"] (spec-paths app "/swagger.json")))
    (is (= #{::swagger/default}
           (-> {:request-method :get :uri "/swagger.json"}
               (app) :body :x-id)))))

(deftest with-options-endpoint-test
  (let [app (ring/ring-handler
              (ring/router
                [["/ping"
                  {:options (constantly "options")}]
                 ["/pong"
                  (constantly "options")]
                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]))]
    (is (= ["/ping" "/pong"] (spec-paths app "/swagger.json")))
    (is (= #{::swagger/default}
           (-> {:request-method :get :uri "/swagger.json"}
               (app) :body :x-id)))))

(deftest all-parameter-types-test
  (let [app (ring/ring-handler
              (ring/router
                [["/parameters"
                  {:post {:coercion spec/coercion
                          :parameters {:query {:q string?}
                                       :body {:b string?}
                                       :form {:f string?}
                                       :header {:h string?}
                                       :path {:p string?}}
                          :handler identity}}]
                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]))
        spec (:body (app {:request-method :get, :uri "/swagger.json"}))]
    (is (= ["query" "body" "formData" "header" "path"]
           (map :in (get-in spec [:paths "/parameters" :post :parameters]))))))
