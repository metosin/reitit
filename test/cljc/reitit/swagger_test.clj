(ns reitit.swagger-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as j]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as malli]
            [reitit.coercion.schema :as schema]
            [reitit.coercion.spec :as spec]
            [reitit.http.interceptors.multipart]
            [reitit.ring :as ring]
            [reitit.ring.malli]
            [reitit.ring.coercion :as rrc]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [schema.core :as s]
            [spec-tools.data-spec :as ds]
            [malli.core :as mc]))

(defn- normalize
  "Normalize format of swagger spec by converting it to json and back.
  Handles differences like :q vs \"q\" in swagger generation."
  [data]
  (-> data
      j/write-value-as-string
      (j/read-value j/keyword-keys-object-mapper)))

(def malli-registry
  (merge
   (mc/base-schemas)
   (mc/predicate-schemas)
   (mc/type-schemas)
   {::req-key [:or :keyword :string]
    ::req-val [:or map? :string]
    ::resp-map map?
    ::resp-string [:string {:min 1}]}))


(def PutReqBody
  (mc/schema [:map-of ::req-key ::req-val] {:registry malli-registry}))

(def PutRespBody
  (mc/schema [:or ::resp-map ::resp-string] {:registry malli-registry}))

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
       {:patch {:summary "patch"
                :operationId "Patch"
                :handler (constantly {:status 200})}
        :options {:summary "options"
                  :middleware [{:data {:swagger {:responses {200 {:description "200"}}}}}]
                  :handler (constantly {:status 200})}
        :get {:summary "plus"
              :operationId "GetPlus"
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
                          {:status 200, :body {:total (+ (reduce + xs) z)}})}
        :put {:summary "plus put with definitions"
              :parameters {:body PutReqBody}
              :responses {200 {:body PutRespBody}
                          500 {:description "fail"}}
              :handler (fn [{{body :body} :parameters}]
                         {:status 200, :body (str "got " body)})}}]]

     ["/schema" {:coercion schema/coercion}
      ["/plus/*z"
       {:get {:summary "plus"
              :parameters {:query {:x s/Int, :y s/Int}
                           :path {:z s/Int}}
              :swagger {:responses {400 {:schema {:type "string"}
                                         :description "kosh"}}}
              :responses {200 {:body {:total s/Int}}
                          500 {:description "fail"}}
              :handler (fn [{{{:keys [x y]} :query
                              {:keys [z]} :path} :parameters}]
                         {:status 200, :body {:total (+ x y z)}})}
        :post {:summary "plus with body"
               :parameters {:body (s/maybe [s/Int])
                            :path {:z s/Int}}
               :swagger {:responses {400 {:schema {:type "string"}
                                          :description "kosh"}}}
               :responses {200 {:body {:total s/Int}}
                           500 {:description "fail"}}
               :handler (fn [{{{:keys [z]} :path
                               xs :body} :parameters}]
                          {:status 200, :body {:total (+ (reduce + xs) z)}})}}]]]

    {:data {:middleware [swagger/swagger-feature
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})))

(deftest swagger-test
  (testing "endpoints work"
    (testing "spec"
      (is (= {:body {:total 6}, :status 200}
             (app {:request-method :get
                   :uri "/api/spec/plus/3"
                   :query-params {:x "2", :y "1"}})))
      (is (= {:body {:total 7}, :status 200}
             (app {:request-method :post
                   :uri "/api/spec/plus/3"
                   :body-params [1 3]}))))
    (testing "schema"
      (is (= {:body {:total 6}, :status 200}
             (app {:request-method :get
                   :uri "/api/schema/plus/3"
                   :query-params {:x "2", :y "1"}})))))

  (testing "swagger-spec"
    (let [spec (:body (app {:request-method :get
                            :uri "/api/swagger.json"}))
          expected {:x-id #{::math}
                    :swagger "2.0"
                    :info {:title "my-api"}
                    :definitions {"reitit.swagger-test/req-key" {:type "string"
                                             :x-anyOf [{:type "string"}
                                                       {:type "string"}]}
                                  "reitit.swagger-test/req-val" {:type "object"
                                             :x-anyOf [{:type "object"}
                                                       {:type "string"}]}
                                  "reitit.swagger-test/resp-map" {:type "object"},
                                  "reitit.swagger-test/resp-string" {:type "string"
                                                 :minLength 1}}
                    :paths {"/api/spec/plus/{z}" {:patch {:parameters []
                                                          :summary "patch"
                                                          :operationId "Patch"
                                                          :responses {:default {:description ""}}}
                                                  :options {:parameters []
                                                            :summary "options"
                                                            :responses {200 {:description "200"}}}
                                                  :get {:parameters [{:in "query"
                                                                      :name "x"
                                                                      :description ""
                                                                      :required true
                                                                      :type "integer"
                                                                      :format "int64"}
                                                                     {:in "query"
                                                                      :name "y"
                                                                      :description ""
                                                                      :required true
                                                                      :type "integer"
                                                                      :format "int64"}
                                                                     {:in "path"
                                                                      :name "z"
                                                                      :description ""
                                                                      :required true
                                                                      :type "integer"
                                                                      :format "int64"}]
                                                        :responses {200 {:description ""
                                                                         :schema {:type "object"
                                                                                  :properties {"total" {:format "int64"
                                                                                                        :type "integer"}}
                                                                                  :required ["total"]}}
                                                                    400 {:schema {:type "string"}
                                                                         :description "kosh"}
                                                                    500 {:description "fail"}}
                                                        :summary "plus"
                                                        :operationId "GetPlus"}
                                                  :post {:parameters [{:in "body",
                                                                       :name "body",
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
                                                         :responses {200 {:schema {:properties {"total" {:format "int64"
                                                                                                         :type "integer"}}
                                                                                   :required ["total"]
                                                                                   :type "object"}
                                                                          :description ""}
                                                                     400 {:schema {:type "string"}
                                                                          :description "kosh"}
                                                                     500 {:description "fail"}}
                                                         :summary "plus with body"}}
                            "/api/malli/plus/{z}" {:get {:parameters [{:in "query"
                                                                       :name :x
                                                                       :description ""
                                                                       :required true
                                                                       :type "integer"
                                                                       :format "int64"}
                                                                      {:in "query"
                                                                       :name :y
                                                                       :description ""
                                                                       :required true
                                                                       :type "integer"
                                                                       :format "int64"}
                                                                      {:in "path"
                                                                       :name :z
                                                                       :description ""
                                                                       :required true
                                                                       :type "integer"
                                                                       :format "int64"}]
                                                         :responses {200 {:schema {:type "object"
                                                                                   :properties {:total {:format "int64"
                                                                                                        :type "integer"}}
                                                                                   :additionalProperties false
                                                                                   :required [:total]}
                                                                          :description ""}
                                                                     400 {:schema {:type "string"}
                                                                          :description "kosh"}
                                                                     500 {:description "fail"}}
                                                         :summary "plus"}
                                                   :post {:parameters [{:in "body",
                                                                        :name "body",
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
                                                                                    :additionalProperties false
                                                                                    :required [:total]
                                                                                    :type "object"}}
                                                                      400 {:schema {:type "string"}
                                                                           :description "kosh"}
                                                                      500 {:description "fail"}}
                                                          :summary "plus with body"}
                                                   :put {:parameters [{:in "body"
                                                                       :name "body"
                                                                       :description ""
                                                                       :required true
                                                                       :schema
                                                                       {:type "object"
                                                                        :additionalProperties
                                                                        {:$ref "#/definitions/reitit.swagger-test~1req-val"}}}]
                                                         :responses {200
                                                                     {:schema
                                                                      {:$ref "#/definitions/reitit.swagger-test~1resp-map"
                                                                       :x-anyOf [{:$ref "#/definitions/reitit.swagger-test~1resp-map"}
                                                                                 {:$ref "#/definitions/reitit.swagger-test~1resp-string"}]}
                                                                      :description ""}
                                                                     500 {:description "fail"}}
                                                         :summary "plus put with definitions"}}
                            "/api/schema/plus/{z}" {:get {:parameters [{:description ""
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
                                                          :summary "plus"}
                                                    :post {:parameters [{:in "body",
                                                                         :name "body",
                                                                         :description "",
                                                                         :required false,
                                                                         :schema {:type "array",
                                                                                  :items {:type "integer",
                                                                                          :format "int32"}
                                                                                  :x-nullable true}}
                                                                        {:in "path"
                                                                         :name "z"
                                                                         :description ""
                                                                         :type "integer"
                                                                         :required true
                                                                         :format "int32"}]
                                                           :responses {200 {:description ""
                                                                            :schema {:properties {"total" {:format "int32"
                                                                                                           :type "integer"}}
                                                                                     :additionalProperties false
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

(deftest swagger-ui-config-test
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

(deftest multiple-content-types-test
  (testing ":request coercion"
    (let [app (ring/ring-handler
               (ring/router
                [["/parameters"
                  {:post {:coercion spec/coercion
                          :request {:content {"application/json" {:x string?}}}
                          :handler identity}}]
                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]))
          output (with-out-str (app {:request-method :get, :uri "/swagger.json"}))]
      (is (.contains output "WARN"))))
  (testing "multiple response content types"
    (let [app (ring/ring-handler
               (ring/router
                [["/parameters"
                  {:post {:coercion spec/coercion
                          :responses {200 {:content {"application/json" {:r string?}}}}
                          :handler identity}}]
                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]))
          output (with-out-str (app {:request-method :get, :uri "/swagger.json"}))]
      (is (.contains output "WARN")))))

(deftest multipart-test
  (doseq [[coercion file-schema string-schema]
          [[#'malli/coercion
            reitit.ring.malli/bytes-part
            :string]
           [#'spec/coercion
            reitit.http.interceptors.multipart/bytes-part
            string?]]]
    (testing (str coercion)
      (let [app (ring/ring-handler
                 (ring/router
                  [["/upload"
                    {:post {:decription "upload"
                            :coercion @coercion
                            :parameters {:multipart {:file file-schema
                                                     :more string-schema}}
                            :handler identity}}]
                   ["/swagger.json"
                    {:get {:no-doc true
                           :handler (swagger/create-swagger-handler)}}]]
                  {:data {:middleware [swagger/swagger-feature]}}))
            spec (-> {:request-method :get
                      :uri "/swagger.json"}
                     app
                     :body)]
        (is (= [{:description ""
                 :in "formData"
                 :name "file"
                 :required true
                 :type "file"}
                {:description ""
                 :in "formData"
                 :name "more"
                 :required true
                 :type "string"}]
               (normalize
                (get-in spec [:paths "/upload" :post :parameters]))))))))

(def X :int)
(def Y :int)
(def Plus [:map
           [:x #'X]
           [:y #'Y]])
(def Result [:map [:result :int]])

(deftest malli-var-test
  (let [app (ring/ring-handler
             (ring/router
              [["/post"
                {:post {:coercion malli/coercion
                        :parameters {:body #'Plus}
                        :responses {200 {:body #'Result}}
                        :handler identity}}]
               ["/get"
                {:get {:coercion malli/coercion
                       :parameters {:query #'Plus}
                       :responses {200 {:body #'Result}}
                       :handler identity}}]
               ["/swagger.json"
                {:get {:no-doc true
                       :handler (swagger/create-swagger-handler)}}]]))
        spec (:body (app {:request-method :get, :uri "/swagger.json"}))]
    (is (= {:definitions {"reitit.swagger-test/Plus" {:properties {:x {:$ref "#/definitions/reitit.swagger-test~1X"},
                                                                   :y {:$ref "#/definitions/reitit.swagger-test~1Y"}},
                                                      :required [:x :y],
                                                      :type "object"},
                          "reitit.swagger-test/X" {:format "int64",
                                                   :type "integer"},
                          "reitit.swagger-test/Y" {:format "int64",
                                                   :type "integer"},
                          "reitit.swagger-test/Result" {:type "object",
                                                        :properties {:result {:type "integer", :format "int64"}},
                                                        :required [:result]}},
            :paths {"/post" {:post {:parameters [{:description "",
                                                  :in "body",
                                                  :name "body",
                                                  :required true,
                                                  :schema {:$ref "#/definitions/reitit.swagger-test~1Plus"}}]
                                    :responses {200 {:description ""
                                                     :schema {:$ref "#/definitions/reitit.swagger-test~1Result"}}}}}
                    "/get" {:get {:parameters [{:in "query"
                                                :name :x
                                                :description ""
                                                :type "integer"
                                                :required true
                                                :format "int64"}
                                               {:in "query"
                                                :name :y
                                                :description ""
                                                :type "integer"
                                                :required true
                                                :format "int64"}]
                                  :responses {200 {:description ""
                                                   :schema {:$ref "#/definitions/reitit.swagger-test~1Result"}}}}}}
            :swagger "2.0",
            :x-id #{:reitit.swagger/default}}
           spec))))
