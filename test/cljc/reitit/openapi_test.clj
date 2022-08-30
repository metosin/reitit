(ns reitit.openapi-test
  (:require [clojure.test :refer [deftest is testing]]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as malli]
            [reitit.coercion.schema :as schema]
            [reitit.coercion.spec :as spec]
            [reitit.openapi :as openapi]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.swagger-ui :as swagger-ui]
            [schema.core :as s]
            [spec-tools.data-spec :as ds]))

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       {:openapi {:id ::math}}

       ["/openapi.json"
        {:get {:no-doc true
               :openapi {:info {:title "my-api"}}
               :handler (openapi/create-openapi-handler)}}]

       #_["/spec" {:coercion spec/coercion}
          ["/plus/:z"
           {:patch {:summary "patch"
                    :handler (constantly {:status 200})}
            :options {:summary "options"
                      :middleware [{:data {:openapi {:responses {200 {:description "200"}}}}}]
                      :handler (constantly {:status 200})}
            :get {:summary "plus"
                  :parameters {:query {:x int?, :y int?}
                               :path {:z int?}}
                  :openapi {:responses {400 {:description "kosh"
                                             :content {"application/json" {:schema {:type "string"}}}}}}
                  :responses {200 {:body {:total int?}}
                              500 {:description "fail"}}
                  :handler (fn [{{{:keys [x y]} :query
                                  {:keys [z]} :path} :parameters}]
                             {:status 200, :body {:total (+ x y z)}})}
            :post {:summary "plus with body"
                   :parameters {:body (ds/maybe [int?])
                                :path {:z int?}}
                   :openapi {:responses {400 {:content {"application/json" {:schema {:type "string"}}}
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
                :openapi {:responses {400 {:description "kosh"
                                           :content {"application/json" {:schema {:type "string"}}}}}}
                :responses {200 {:body [:map [:total int?]]}
                            500 {:description "fail"}}
                :handler (fn [{{{:keys [x y]} :query
                                {:keys [z]} :path} :parameters}]
                           {:status 200, :body {:total (+ x y z)}})}
          :post {:summary "plus with body"
                 :parameters {:body [:maybe [:vector int?]]
                              :path [:map [:z int?]]}
                 :openapi {:responses {400 {:description "kosh"
                                            :content {"application/json" {:schema {:type "string"}}}}}}
                 :responses {200 {:body [:map [:total int?]]}
                             500 {:description "fail"}}
                 :handler (fn [{{{:keys [z]} :path
                                 xs :body} :parameters}]
                            {:status 200, :body {:total (+ (reduce + xs) z)}})}}]]

       ["/schema" {:coercion schema/coercion}
        ["/plus/*z"
         {:get {:summary "plus"
                :parameters {:query {:x s/Int, :y s/Int}
                             :path {:z s/Int}}
                :openapi {:responses {400 {:content {"application/json" {:schema {:type "string"}}}
                                           :description "kosh"}}}
                :responses {200 {:body {:total s/Int}}
                            500 {:description "fail"}}
                :handler (fn [{{{:keys [x y]} :query
                                {:keys [z]} :path} :parameters}]
                           {:status 200, :body {:total (+ x y z)}})}
          :post {:summary "plus with body"
                 :parameters {:body (s/maybe [s/Int])
                              :path {:z s/Int}}
                 :openapi {:responses {400 {:content {"application/json" {:schema {:type "string"}}}
                                            :description "kosh"}}}
                 :responses {200 {:body {:total s/Int}}
                             500 {:description "fail"}}
                 :handler (fn [{{{:keys [z]} :path
                                 xs :body} :parameters}]
                            {:status 200, :body {:total (+ (reduce + xs) z)}})}}]]]

      {:data {:middleware [openapi/openapi-feature
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))

(require '[fipp.edn])
(deftest openapi-test
  (testing "endpoints work"
    (testing "malli"
      (is (= {:body {:total 6}, :status 200}
             (app {:request-method :get
                   :uri "/api/malli/plus/3"
                   :query-params {:x "2", :y "1"}})))
      (is (= {:body {:total 7}, :status 200}
             (app {:request-method :post
                   :uri "/api/malli/plus/3"
                   :body-params [1 3]})))))
  (testing "openapi-spec"
    (let [spec (:body (app {:request-method :get
                            :uri "/api/openapi.json"}))
          expected {:x-id #{::math}
                    :openapi "3.1.0"
                    :info {:title "my-api"}
                    :paths {#_#_"/api/spec/plus/{z}" {:patch {:summary "patch"
                                                              :responses {:default {:description ""}}}
                                                      :options {:summary "options"
                                                                :responses {200 {:description "200"}}}
                                                      :get {:parameters [{:in "query"
                                                                          :name "x"
                                                                          :description ""
                                                                          :required true
                                                                          :schema {:type "integer"}}
                                                                         {:in "query"
                                                                          :name "y"
                                                                          :description ""
                                                                          :required true
                                                                          :schema {:type "integer"}}
                                                                         {:in "path"
                                                                          :name "z"
                                                                          :description ""
                                                                          :required true
                                                                          :schema {:type "integer"}}]
                                                            :responses {200 {:content {"application/json" {:schema {:type "object"
                                                                                                                    :properties {"total" {:format "int64"
                                                                                                                                          :type "integer"}}
                                                                                                                    :required ["total"]}}}}
                                                                        400 {:description "kosh"
                                                                             :content {"application/json" {:schema {:type "string"}}}}
                                                                        500 {:description "fail"}}
                                                            :summary "plus"}
                                                      :post {:parameters [{:in "path"
                                                                           :name "z"
                                                                           :required true
                                                                           :schema {:type "integer"}}]
                                                             :requestBody {:content {"application/json" {:schema {:oneOf [{:items {:type "integer"}
                                                                                                                           :type "array"}
                                                                                                                          {:type "null"}]}}}}
                                                             :responses {200 {:content {"application/json" {:schema {:properties {"total" {:format "int64"
                                                                                                                                           :type "integer"}}
                                                                                                                     :required ["total"]
                                                                                                                     :type "object"}}}}
                                                                         400 {:content {"application/json" {:schema {:type "string"}}}
                                                                              :description "kosh"}
                                                                         500 {:description "fail"}}
                                                             :summary "plus with body"}}
                            "/api/malli/plus/{z}" {:get {:parameters [{:in "query"
                                                                       :name :x
                                                                       :required true
                                                                       :schema {:type "integer"}}
                                                                      {:in "query"
                                                                       :name :y
                                                                       :required true
                                                                       :schema {:type "integer"}}
                                                                      {:in "path"
                                                                       :name :z
                                                                       :required true
                                                                       :schema {:type "integer"}}]
                                                         :responses {200 {:content {"application/json" {:schema {:type "object"
                                                                                                                 :properties {:total {:type "integer"}}
                                                                                                                 :required [:total]}}}}
                                                                     400 {:description "kosh"
                                                                          :content {"application/json" {:schema {:type "string"}}}}
                                                                     500 {:description "fail"}}
                                                         :summary "plus"}
                                                   :post {:parameters [{:in "path"
                                                                        :name :z
                                                                        :schema {:type "integer"}
                                                                        :required true}]
                                                          :requestBody {:content {"application/json" {:schema {:oneOf [{:items {:type "integer"}
                                                                                                                        :type "array"}
                                                                                                                       {:type "null"}]}}}}
                                                          :responses {200 {:content {"application/json" {:schema {:properties {:total {:type "integer"}}
                                                                                                                  :required [:total]
                                                                                                                  :type "object"}}}}
                                                                      400 {:description "kosh"
                                                                           :content {"application/json" {:schema {:type "string"}}}}
                                                                      500 {:description "fail"}}
                                                          :summary "plus with body"}}
                            "/api/schema/plus/{z}" {:get {:parameters [{:description ""
                                                                        :in "query"
                                                                        :name "x"
                                                                        :required true
                                                                        :schema {:format "int32"
                                                                                 :type "integer"}}
                                                                       {:description ""
                                                                        :in "query"
                                                                        :name "y"
                                                                        :required true
                                                                        :schema {:type "integer"
                                                                                 :format "int32"}}
                                                                       {:in "path"
                                                                        :name "z"
                                                                        :description ""
                                                                        :required true
                                                                        :schema {:type "integer"
                                                                                 :format "int32"}}]
                                                          :responses {200 {:content {"application/json" {:schema {:additionalProperties false
                                                                                                                  :properties {"total" {:format "int32"
                                                                                                                                        :type "integer"}}
                                                                                                                  :required ["total"]
                                                                                                                  :type "object"}}}}
                                                                      400 {:description "kosh"
                                                                           :content {"application/json" {:schema {:type "string"}}}}
                                                                      500 {:description "fail"}}
                                                          :summary "plus"}
                                                    :post {:parameters [{:in "path"
                                                                         :name "z"
                                                                         :description ""
                                                                         :required true
                                                                         :schema {:type "integer"
                                                                                  :format "int32"}}]
                                                           :requestBody {:content {"application/json" {:schema {:oneOf [{:type "array"
                                                                                                                         :items {:type "integer"
                                                                                                                                 :format "int32"}}
                                                                                                                        {:type "null"}]}}}}
                                                           :responses {200 {:content {"application/json" {:schema {:properties {"total" {:format "int32"
                                                                                                                                         :type "integer"}}
                                                                                                                   :additionalProperties false
                                                                                                                   :required ["total"]
                                                                                                                   :type "object"}}}}
                                                                       400 {:description "kosh"
                                                                            :content {"application/json" {:schema {:type "string"}}}}
                                                                       500 {:description "fail"}}
                                                           :summary "plus with body"}}}}]
      (is (= expected spec)))))

(defn spec-paths [app uri]
  (-> {:request-method :get, :uri uri} app :body :paths keys))

(deftest multiple-openapi-apis-test
  (let [ping-route ["/ping" {:get (constantly "ping")}]
        spec-route ["/openapi.json"
                    {:get {:no-doc true
                           :handler (openapi/create-openapi-handler)}}]
        app (ring/ring-handler
              (ring/router
                [["/common" {:openapi {:id #{::one ::two}}}
                  ping-route]

                 ["/one" {:openapi {:id ::one}}
                  ping-route
                  spec-route]

                 ["/two" {:openapi {:id ::two}}
                  ping-route
                  spec-route
                  ["/deep" {:openapi {:id ::one}}
                   ping-route]]
                 ["/one-two" {:openapi {:id #{::one ::two}}}
                  spec-route]]))]
    (is (= ["/common/ping" "/one/ping" "/two/deep/ping"]
           (spec-paths app "/one/openapi.json")))
    (is (= ["/common/ping" "/two/ping"]
           (spec-paths app "/two/openapi.json")))
    (is (= ["/common/ping" "/one/ping" "/two/ping" "/two/deep/ping"]
           (spec-paths app "/one-two/openapi.json")))))

(deftest openapi-ui-config-test
  (let [app (swagger-ui/create-swagger-ui-handler
              {:path "/"
               :url "/openapi.json"
               :config {:jsonEditor true}})]
    (is (= 302 (:status (app {:request-method :get, :uri "/"}))))
    (is (= 200 (:status (app {:request-method :get, :uri "/index.html"}))))
    (is (= {:jsonEditor true, :url "/openapi.json"}
           (->> {:request-method :get, :uri "/config.json"}
                (app) :body (m/decode m/instance "application/json"))))))

(deftest without-openapi-id-test
  (let [app (ring/ring-handler
              (ring/router
                [["/ping"
                  {:get (constantly "ping")}]
                 ["/openapi.json"
                  {:get {:no-doc true
                         :handler (openapi/create-openapi-handler)}}]]))]
    (is (= ["/ping"] (spec-paths app "/openapi.json")))
    (is (= #{::openapi/default}
           (-> {:request-method :get :uri "/openapi.json"}
               (app) :body :x-id)))))

(deftest with-options-endpoint-test
  (let [app (ring/ring-handler
              (ring/router
                [["/ping"
                  {:options (constantly "options")}]
                 ["/pong"
                  (constantly "options")]
                 ["/openapi.json"
                  {:get {:no-doc true
                         :handler (openapi/create-openapi-handler)}}]]))]
    (is (= ["/ping" "/pong"] (spec-paths app "/openapi.json")))
    (is (= #{::openapi/default}
           (-> {:request-method :get :uri "/openapi.json"}
               (app) :body :x-id)))))

(deftest malli-all-parameter-types-test
  (let [app (ring/ring-handler
              (ring/router
                [["/parameters"
                  {:post {:coercion malli/coercion
                          :parameters {:query [:map
                                               [:q :string]]
                                       :body [:map
                                              [:b :string]]
                                       :header [:map
                                                [:h :string]]
                                       :cookie [:map
                                                [:c :string]]
                                       :path [:map
                                              [:p :string]]}
                          :responses {200 {:body [:map [:ok :string]]}}
                          :handler identity}}]
                 ["/openapi.json"
                  {:get {:handler (openapi/create-openapi-handler)
                         :no-doc true}}]]))
        spec (-> {:request-method :get
                  :uri "/openapi.json"}
                 app
                 :body)]
    (testing
      "all non-body parameters"
      (is (= [{:in "query"
               :name :q
               :required true
               :schema {:type "string"}}
              {:in "header"
               :name :h
               :required true
               :schema {:type "string"}}
              {:in "cookie"
               :name :c
               :required true
               :schema {:type "string"}}
              {:in "path"
               :name :p
               :required true
               :schema {:type "string"}}]
             (-> spec
                 (get-in [:paths "/parameters" :post :parameters])
                 #_(doto clojure.pprint/pprint)))))
    (testing
      "body parameter"
      (is (= {"application/json" {:schema {:type "object"
                                           :properties {:b {:type "string"}}
                                           :required [:b]}}}
             (-> spec
                 (get-in [:paths "/parameters" :post :requestBody :content])
                 #_(doto clojure.pprint/pprint)))))
    (testing
      "body response"
      (is (= {"application/json" {:schema {:type "object"
                                           :properties {:ok {:type "string"}}
                                           :required [:ok]}}}
             (-> spec
                 (get-in [:paths "/parameters" :post :responses 200 :content])
                 #_(doto clojure.pprint/pprint)))))))

(deftest malli-all-parameter-types-test-per-content-type
  (let [app (ring/ring-handler
              (ring/router
                [["/parameters"
                  {:post {:coercion malli/coercion
                          :parameters {:query [:map
                                               [:q :string]]
                                       :request {:content {"application/json" [:map
                                                                               [:b :string]]}}
                                       :header [:map
                                                [:h :string]]
                                       :cookie [:map
                                                [:c :string]]
                                       :path [:map
                                              [:p :string]]}
                          :responses {200 {:content {"application/json" [:map [:ok :string]]}}}
                          :handler identity}}]
                 ["/openapi.json"
                  {:get {:handler (openapi/create-openapi-handler)
                         :no-doc true}}]]))
        spec (-> {:request-method :get
                  :uri "/openapi.json"}
                 app
                 :body)]
    (testing
      "all non-body parameters"
      (is (= [{:in "query"
               :name :q
               :required true
               :schema {:type "string"}}
              {:in "header"
               :name :h
               :required true
               :schema {:type "string"}}
              {:in "cookie"
               :name :c
               :required true
               :schema {:type "string"}}
              {:in "path"
               :name :p
               :required true
               :schema {:type "string"}}]
             (-> spec
                 (get-in [:paths "/parameters" :post :parameters])
                 #_(doto clojure.pprint/pprint)))))
    (testing
      "body parameter"
      (is (= {"application/json" {:schema {:type "object"
                                           :properties {:b {:type "string"}}
                                           :required [:b]}}}
             (-> spec
                 (get-in [:paths "/parameters" :post :requestBody :content])
                 #_(doto clojure.pprint/pprint)))))
    (testing
      "body response"
      (is (= {"application/json" {:schema {:type "object"
                                           :properties {:ok {:type "string"}}
                                           :required [:ok]}}}
             (-> spec
                 (get-in [:paths "/parameters" :post :responses 200 :content])
                 #_(doto clojure.pprint/pprint)))))))


(deftest schema-all-parameter-types-test-per-content-type
  (let [app (ring/ring-handler
              (ring/router
                [["/parameters"
                  {:post {:coercion schema/coercion
                          :parameters {:query {:q s/Str}
                                       :request {:content {"application/json" {:b s/Str}}}
                                       :header {:h s/Str}
                                       :cookie {:c s/Str}
                                       :path {:p s/Str}}
                          :responses {200 {:content {"application/json" {:ok s/Str}}}}
                          :handler identity}}]
                 ["/openapi.json"
                  {:get {:handler (openapi/create-openapi-handler)
                         :no-doc true}}]]))
        spec (-> {:request-method :get
                  :uri "/openapi.json"}
                 app
                 :body)]
    (testing
      "all non-body parameters"
      (is (= [{:description ""
               :in "query"
               :name "q"
               :required true
               :schema {:type "string"}}
              {:description ""
               :in "header"
               :name "h"
               :required true
               :schema {:type "string"}}
              {:description ""
               :in "cookie"
               :name "c"
               :required true
               :schema {:type "string"}}
              {:description ""
               :in "path"
               :name "p"
               :required true
               :schema {:type "string"}}]
             (-> spec
                 (get-in [:paths "/parameters" :post :parameters])
                 #_(doto clojure.pprint/pprint)))))
    (testing
      "body parameter"
      (is (= {"application/json" {:schema {:additionalProperties false
                                           :properties {"b" {:type "string"}}
                                           :required ["b"]
                                           :type "object"}}}
             (-> spec
                 (get-in [:paths "/parameters" :post :requestBody :content])
                 #_(doto clojure.pprint/pprint)))))
    (testing
      "body response"
      (is (= {"application/json" {:schema {:additionalProperties false
                                           :properties {"ok" {:type "string"}}
                                           :required ["ok"]
                                           :type "object"}}}
             (-> spec
                 (get-in [:paths "/parameters" :post :responses 200 :content])
                 #_(doto clojure.pprint/pprint)))))))
(deftest all-parameter-types-test
  (let [app (ring/ring-handler
              (ring/router
                [["/parameters"
                  {:post {:coercion spec/coercion
                          :parameters {:query {:q string?}
                                       :body {:b string?}
                                       :cookies {:c string?}
                                       :header {:h string?}
                                       :path {:p string?}}
                          :responses {200 {:body {:ok string?}}}
                          :handler identity}}]
                 ["/openapi.json"
                  {:get {:no-doc true
                         :handler (openapi/create-openapi-handler)}}]]))
        spec (:body (app {:request-method :get, :uri "/openapi.json"}))]
    (is (= [{:description ""
             :in "query"
             :name "q"
             :required true
             :schema {:type "string"}}
            {:description ""
             :in "cookies"
             :name "c"
             :required true
             :schema {:type "string"}}
            {:description ""
             :in "header"
             :name "h"
             :required true
             :schema {:type "string"}}
            {:description ""
             :in "path"
             :name "p"
             :required true
             :schema {:type "string"}}]
           (-> spec
               (get-in [:paths "/parameters" :post :parameters])
               #_(doto clojure.pprint/pprint))))
    (is (= {"application/json" {:schema {:properties {"b" {:type "string"}}
                                         :required ["b"]
                                         :type "object"}}}
           (-> spec
               (get-in [:paths "/parameters" :post :requestBody :content])
               #_(doto clojure.pprint/pprint))))
    (is (= {"application/json" {:schema {:properties {"ok" {:type "string"}}
                                         :required ["ok"]
                                         :type "object"}}}
           (-> spec
               (get-in [:paths "/parameters" :post :responses 200 :content])
               #_(doto clojure.pprint/pprint))))))
