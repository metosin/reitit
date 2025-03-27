(ns reitit.openapi-test
  (:require [clojure.java.shell :as shell]
            [clojure.test :refer [deftest is testing]]
            [jsonista.core :as j]
            [malli.core :as mc]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as matchers]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as malli]
            [reitit.coercion.schema :as schema]
            [reitit.coercion.spec :as spec]
            [reitit.http.interceptors.multipart]
            [reitit.openapi :as openapi]
            [reitit.ring :as ring]
            [reitit.ring.malli]
            [reitit.ring.spec]
            [reitit.ring.coercion :as rrc]
            [reitit.swagger-ui :as swagger-ui]
            [schema.core :as s]
            [schema-tools.core]
            [spec-tools.core :as st]
            [spec-tools.data-spec :as ds]))

(defn validate
  "Returns nil if data is a valid openapi spec, otherwise validation result"
  [data]
  (let [file (java.io.File/createTempFile "reitit-openapi" ".json")]
    (.deleteOnExit file)
    (spit file (j/write-value-as-string data))
    (let [result (shell/sh "npx" "-p" "@seriousme/openapi-schema-validator" "validate-api" (.getPath file))]
      (when-not (zero? (:exit result))
        (j/read-value (:out result))))))

(def app
  (ring/ring-handler
   (ring/router
    ["/api"
     {:openapi {:id ::math}}

     ["/openapi.json"
      {:get {:no-doc true
             :openapi {:info {:title "my-api"
                              :version "0.0.1"}}
             :handler (openapi/create-openapi-handler)}}]

     ["/spec" {:coercion spec/coercion}
      ["/plus/:z"
       {:get {:summary "plus"
              :tags [:plus :spec]
              :parameters {:query {:x int?, :y int?}
                           :path {:z int?}}
              :openapi {:operationId "spec-plus"
                        :deprecated true
                        :responses {400 {:description "kosh"
                                         :content {"application/json" {:schema {:type "string"}}}}}}
              :responses {200 {:description "success"
                               :body {:total int?}}
                          500 {:description "fail"}}
              :handler (fn [{{{:keys [x y]} :query
                              {:keys [z]} :path} :parameters}]
                         {:status 200, :body {:total (+ x y z)}})}
        :post {:summary "plus with body"
               :parameters {:body (ds/maybe [int?])
                            :path {:z int?}}
               :openapi {:responses {400 {:content {"application/json" {:schema {:type "string"}}}
                                          :description "kosh"}}}
               :responses {200 {:description "success"
                                :body {:total int?}}
                           500 {:description "fail"}
                           504 {:description "default"
                                :content {:default {:schema {:error string?}}}
                                :body {:masked string?}}}
               :handler (fn [{{{:keys [z]} :path
                               xs :body} :parameters}]
                          {:status 200, :body {:total (+ (reduce + xs) z)}})}}]]

     ["/malli" {:coercion malli/coercion}
      ["/plus/*z"
       {:get {:summary "plus"
              :tags [:plus :malli]
              :parameters {:query [:map [:x int?] [:y int?]]
                           :path [:map [:z int?]]}
              :openapi {:responses {400 {:description "kosh"
                                         :content {"application/json" {:schema {:type "string"}}}}}}
              :responses {200 {:description "success"
                               :body [:map [:total int?]]}
                          500 {:description "fail"}}
              :handler (fn [{{{:keys [x y]} :query
                              {:keys [z]} :path} :parameters}]
                         {:status 200, :body {:total (+ x y z)}})}
        :post {:summary "plus with body"
               :parameters {:body [:maybe [:vector int?]]
                            :path [:map [:z int?]]}
               :openapi {:responses {400 {:description "kosh"
                                          :content {"application/json" {:schema {:type "string"}}}}}}
               :responses {200 {:description "success"
                                :body [:map [:total int?]]}
                           500 {:description "fail"}
                           504 {:description "default"
                                :content {:default {:schema {:error string?}}}
                                :body {:masked string?}}}
               :handler (fn [{{{:keys [z]} :path
                               xs :body} :parameters}]
                          {:status 200, :body {:total (+ (reduce + xs) z)}})}}]]

     ["/schema" {:coercion schema/coercion}
      ["/plus/*z"
       {:get {:summary "plus"
              :tags [:plus :schema]
              :parameters {:query {:x s/Int, :y s/Int}
                           :path {:z s/Int}}
              :openapi {:responses {400 {:content {"application/json" {:schema {:type "string"}}}
                                         :description "kosh"}}}
              :responses {200 {:description "success"
                               :body {:total s/Int}}
                          500 {:description "fail"}}
              :handler (fn [{{{:keys [x y]} :query
                              {:keys [z]} :path} :parameters}]
                         {:status 200, :body {:total (+ x y z)}})}
        :post {:summary "plus with body"
               :parameters {:body (s/maybe [s/Int])
                            :path {:z s/Int}}
               :openapi {:responses {400 {:content {"application/json" {:schema {:type "string"}}}
                                          :description "kosh"}}}
               :responses {200 {:description "success"
                                :body {:total s/Int}}
                           500 {:description "fail"}
                           504 {:description "default"
                                :content {:default {:schema {:error s/Str}}}
                                :body {:masked s/Str}}}
               :handler (fn [{{{:keys [z]} :path
                               xs :body} :parameters}]
                          {:status 200, :body {:total (+ (reduce + xs) z)}})}}]]]

    {:validate reitit.ring.spec/validate
     :data {:middleware [openapi/openapi-feature
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})))

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
                    :info {:title "my-api"
                           :version "0.0.1"}
                    :paths {"/api/spec/plus/{z}" {:get {:parameters [{:in "query"
                                                                      :name "x"
                                                                      :required true
                                                                      :schema {:type "integer"
                                                                               :format "int64"}}
                                                                     {:in "query"
                                                                      :name "y"
                                                                      :required true
                                                                      :schema {:type "integer"
                                                                               :format "int64"}}
                                                                     {:in "path"
                                                                      :name "z"
                                                                      :required true
                                                                      :schema {:type "integer"
                                                                               :format "int64"}}]
                                                        :responses {200 {:description "success"
                                                                         :content {"application/json" {:schema {:type "object"
                                                                                                                :properties {"total" {:format "int64"
                                                                                                                                      :type "integer"}}
                                                                                                                :required ["total"]}}}}
                                                                    400 {:description "kosh"
                                                                         :content {"application/json" {:schema {:type "string"}}}}
                                                                    500 {:description "fail"}}
                                                        :operationId "spec-plus"
                                                        :deprecated true
                                                        :tags [:plus :spec]
                                                        :summary "plus"}
                                                  :post {:parameters [{:in "path"
                                                                       :name "z"
                                                                       :required true
                                                                       :schema {:type "integer"
                                                                                :format "int64"}}]
                                                         :requestBody {:content {"application/json" {:schema {:oneOf [{:items {:type "integer"
                                                                                                                               :format "int64"}
                                                                                                                       :type "array"}
                                                                                                                      {:type "null"}]}}}}
                                                         :responses {200 {:description "success"
                                                                          :content {"application/json" {:schema {:properties {"total" {:format "int64"
                                                                                                                                       :type "integer"}}
                                                                                                                 :required ["total"]
                                                                                                                 :type "object"}}}}
                                                                     400 {:content {"application/json" {:schema {:type "string"}}}
                                                                          :description "kosh"}
                                                                     500 {:description "fail"}
                                                                     504 {:description "default"
                                                                          :content {"application/json" {:schema {:properties {"error" {:type "string"}}
                                                                                                                 :required ["error"]
                                                                                                                 :type "object"}}}}}
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
                                                         :responses {200 {:description "success"
                                                                          :content {"application/json" {:schema {:type "object"
                                                                                                                 :properties {:total {:type "integer"}}
                                                                                                                 :additionalProperties false
                                                                                                                 :required [:total]}}}}
                                                                     400 {:description "kosh"
                                                                          :content {"application/json" {:schema {:type "string"}}}}
                                                                     500 {:description "fail"}}
                                                         :tags [:plus :malli]
                                                         :summary "plus"}
                                                   :post {:parameters [{:in "path"
                                                                        :name :z
                                                                        :schema {:type "integer"}
                                                                        :required true}]
                                                          :requestBody {:content {"application/json" {:schema {:oneOf [{:items {:type "integer"}
                                                                                                                        :type "array"}
                                                                                                                       {:type "null"}]}}}}
                                                          :responses {200 {:description "success"
                                                                           :content {"application/json" {:schema {:properties {:total {:type "integer"}}
                                                                                                                  :required [:total]
                                                                                                                  :additionalProperties false
                                                                                                                  :type "object"}}}}
                                                                      400 {:description "kosh"
                                                                           :content {"application/json" {:schema {:type "string"}}}}
                                                                      500 {:description "fail"}
                                                                      504 {:description "default"
                                                                           :content {"application/json" {:schema {:additionalProperties false
                                                                                                                  :properties {:error {:type "string"}}
                                                                                                                  :required [:error]
                                                                                                                  :type "object"}}}}}
                                                          :summary "plus with body"}}
                            "/api/schema/plus/{z}" {:get {:parameters [{:in "query"
                                                                        :name "x"
                                                                        :required true
                                                                        :schema {:format "int32"
                                                                                 :type "integer"}}
                                                                       {:in "query"
                                                                        :name "y"
                                                                        :required true
                                                                        :schema {:type "integer"
                                                                                 :format "int32"}}
                                                                       {:in "path"
                                                                        :name "z"
                                                                        :required true
                                                                        :schema {:type "integer"
                                                                                 :format "int32"}}]
                                                          :responses {200 {:description "success"
                                                                           :content {"application/json" {:schema {:additionalProperties false
                                                                                                                  :properties {"total" {:format "int32"
                                                                                                                                        :type "integer"}}
                                                                                                                  :required ["total"]
                                                                                                                  :type "object"}}}}
                                                                      400 {:description "kosh"
                                                                           :content {"application/json" {:schema {:type "string"}}}}
                                                                      500 {:description "fail"}}
                                                          :tags [:plus :schema]
                                                          :summary "plus"}
                                                    :post {:parameters [{:in "path"
                                                                         :name "z"
                                                                         :required true
                                                                         :schema {:type "integer"
                                                                                  :format "int32"}}]
                                                           :requestBody {:content {"application/json" {:schema {:oneOf [{:type "array"
                                                                                                                         :items {:type "integer"
                                                                                                                                 :format "int32"}}
                                                                                                                        {:type "null"}]}}}}
                                                           :responses {200 {:description "success"
                                                                            :content {"application/json" {:schema {:properties {"total" {:format "int32"
                                                                                                                                         :type "integer"}}
                                                                                                                   :additionalProperties false
                                                                                                                   :required ["total"]
                                                                                                                   :type "object"}}}}
                                                                       400 {:description "kosh"
                                                                            :content {"application/json" {:schema {:type "string"}}}}
                                                                       500 {:description "fail"}
                                                                       504 {:description "default"
                                                                            :content {"application/json" {:schema {:additionalProperties false
                                                                                                                   :properties {"error" {:type "string"}}
                                                                                                                   :required ["error"]
                                                                                                                   :type "object"}}}}}
                                                           :summary "plus with body"}}}}]
      (is (= expected spec))
      (is (= nil (validate spec))))))

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
    (is (= 200 (:status (app {:request-method :get, :uri "/"}))))
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

(defn- normalize
  "Normalize format of openapi spec by converting it to json and back.
  Handles differences like :q vs \"q\" in openapi generation."
  [data]
  (-> data
      j/write-value-as-string
      (j/read-value j/keyword-keys-object-mapper)))

(deftest all-parameter-types-test
  (doseq [[coercion ->schema]
          [[#'malli/coercion (fn [nom] [:map [nom [:string {:description (str "description " nom)}]]])]
           [#'schema/coercion (fn [nom] {nom (schema-tools.core/schema s/Str
                                                                       {:description (str "description " nom)})})]
           [#'spec/coercion (fn [nom] {nom (st/spec {:spec string?
                                                     :description (str "description " nom)})})]]]
    (testing (str coercion)
      (let [app (ring/ring-handler
                 (ring/router
                  [["/parameters"
                    {:post {:decription "parameters"
                            :coercion @coercion
                            :parameters {:query (->schema :q)
                                         :body (->schema :b)
                                         :header (->schema :h)
                                         :cookie (->schema :c)
                                         :path (->schema :p)}
                            :responses {200 {:description "success"
                                             :body (->schema :ok)}}
                            :handler identity}}]
                   ["/openapi.json"
                    {:get {:handler (openapi/create-openapi-handler)
                           :openapi {:info {:title "" :version "0.0.1"}}
                           :no-doc true}}]]
                  {:data {:middleware [openapi/openapi-feature]}}))
            spec (-> {:request-method :get
                      :uri "/openapi.json"}
                     app
                     :body)]
        (testing "all non-body parameters"
          (is (match? [{:in "query"
                        :name "q"
                        :required true
                        :description "description :q"
                        :schema {:type "string"}}
                       {:in "header"
                        :name "h"
                        :required true
                        :description "description :h"
                        :schema {:type "string"}}
                       {:in "cookie"
                        :name "c"
                        :required true
                        :description "description :c"
                        :schema {:type "string"}}
                       {:in "path"
                        :name "p"
                        :required true
                        :description "description :p"
                        :schema {:type "string"}}]
                      (-> spec
                          (get-in [:paths "/parameters" :post :parameters])
                          normalize))))
        (testing "body parameter"
          (is (match? (merge {:type "object"
                              :properties {:b {:type "string"}}
                              :required ["b"]}
                             ;; spec outputs open schemas
                             (when-not (#{#'spec/coercion} coercion)
                               {:additionalProperties false}))
                      (-> spec
                          (get-in [:paths "/parameters" :post :requestBody :content "application/json" :schema])
                          normalize))))
        (testing "body response"
          (is (match? (merge {:type "object"
                              :properties {:ok {:type "string"}}
                              :required ["ok"]}
                             (when-not (#{#'spec/coercion} coercion)
                               {:additionalProperties false}))
                      (-> spec
                          (get-in [:paths "/parameters" :post :responses 200 :content "application/json" :schema])
                          normalize))))
        (testing "spec is valid"
          (is (nil? (validate spec))))))))

(deftest examples-test
  (doseq [[coercion ->schema]
          [[#'malli/coercion (fn [nom] [:map
                                        {:json-schema/example {nom "EXAMPLE2"}}
                                        [nom [:string {:json-schema/example "EXAMPLE"}]]])]
           [#'schema/coercion (fn [nom] (schema-tools.core/schema
                                         {nom (schema-tools.core/schema s/Str {:openapi/example "EXAMPLE"})}
                                         {:openapi/example {nom "EXAMPLE2"}}))]
           [#'spec/coercion (fn [nom]
                              (assoc
                                (ds/spec ::foo {nom (st/spec string? {:openapi/example "EXAMPLE"})})
                                :openapi/example {nom "EXAMPLE2"}))]]]
    (testing (str coercion)
      (let [app (ring/ring-handler
                 (ring/router
                  [["/examples"
                    {:post {:decription "examples"
                            :openapi/request-content-types ["application/json" "application/edn"]
                            :openapi/response-content-types ["application/json" "application/edn"]
                            :coercion @coercion
                            :request {:content {"application/json" {:schema (->schema :b)
                                                                    :examples {"named-example" {:description "a named example"
                                                                                                :value {:b "named"}}}}
                                                :default {:schema (->schema :b2)
                                                          :examples {"default-example" {:description "default example"
                                                                                        :value {:b2 "named"}}}}}}
                            :parameters {:query (->schema :q)}
                            :responses {200 {:description "success"
                                             :content {"application/json" {:schema (->schema :ok)
                                                                           :examples {"response-example" {:value {:ok "response"}}}}
                                                       :default {:schema (->schema :ok)
                                                                 :examples {"default-response-example" {:value {:ok "default"}}}}}}}
                            :handler identity}}]
                   ["/openapi.json"
                    {:get {:handler (openapi/create-openapi-handler)
                           :openapi {:info {:title "" :version "0.0.1"}}
                           :no-doc true}}]]
                  {:data {:middleware [openapi/openapi-feature]}}))
            spec (-> {:request-method :get
                      :uri "/openapi.json"}
                     app
                     :body)]
        (testing "query parameter"
          (is (match? [{:in "query"
                        :name "q"
                        :required true
                        :schema {:type "string"
                                 :example "EXAMPLE"}}]
                      (-> spec
                          (get-in [:paths "/examples" :post :parameters])
                          normalize))))
        (testing "body parameter"
          (is (match? {:schema {:type "object"
                                :properties {:b {:type "string"
                                                 :example "EXAMPLE"}}
                                :required ["b"]
                                :example {:b "EXAMPLE2"}}
                       :examples {:named-example {:description "a named example"
                                                  :value {:b "named"}}}}
                      (-> spec
                          (get-in [:paths "/examples" :post :requestBody :content "application/json"])
                          normalize)))
          (testing "default"
            (is (match? {:schema {:type "object"
                                  :properties {:b2 {:type "string"
                                                    :example "EXAMPLE"}}
                                  :required ["b2"]
                                  :example {:b2 "EXAMPLE2"}}
                         :examples {:default-example {:description "default example"
                                                      :value {:b2 "named"}}}}
                        (-> spec
                            (get-in [:paths "/examples" :post :requestBody :content "application/edn"])
                            normalize)))))
        (testing "body response"
          (is (match? {:schema {:type "object"
                                :properties {:ok {:type "string"
                                                  :example "EXAMPLE"}}
                                :required ["ok"]
                                :example {:ok "EXAMPLE2"}}
                       :examples {:response-example {:value {:ok "response"}}}}
                      (-> spec
                          (get-in [:paths "/examples" :post :responses 200 :content "application/json"])
                          normalize)))
          (testing "default"
            (is (match? {:schema {:type "object"
                                  :properties {:ok {:type "string"
                                                    :example "EXAMPLE"}}
                                  :required ["ok"]
                                  :example {:ok "EXAMPLE2"}}
                         :examples {:default-response-example {:value {:ok "default"}}}}
                        (-> spec
                            (get-in [:paths "/examples" :post :responses 200 :content "application/edn"])
                            normalize)))))
        (testing "spec is valid"
          (is (nil? (validate spec))))))))

(deftest multipart-test
  (doseq [[coercion file-schema string-schema] [[#'malli/coercion
                                                 reitit.ring.malli/bytes-part
                                                 :string]
                                                [#'schema/coercion
                                                 (schema-tools.core/schema {:filename s/Str
                                                                            :content-type s/Str
                                                                            :bytes s/Num}
                                                                           {:openapi {:type "string"
                                                                                      :format "binary"}})
                                                 s/Str]
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
                   ["/openapi.json"
                    {:get {:handler (openapi/create-openapi-handler)
                           :openapi {:info {:title "" :version "0.0.1"}}
                           :no-doc true}}]]
                  {:data {:middleware [openapi/openapi-feature]}}))
            spec (-> {:request-method :get
                      :uri "/openapi.json"}
                     app
                     :body)]
        (testing "multipart body"
          (is (nil? (get-in spec [:paths "/upload" :post :parameters])))
          (is (= (merge {:type "object"
                         :properties {:file {:type "string"
                                             :format "binary"}
                                      :more {:type "string"}}
                         :required ["file" "more"]}
                        (when-not (= #'spec/coercion coercion)
                          {:additionalProperties false}))
                 (-> spec
                     (get-in [:paths "/upload" :post :requestBody :content "multipart/form-data" :schema])
                     normalize))))
        (testing "spec is valid"
          (is (nil? (validate spec))))))))

(deftest per-content-type-test
  (doseq [[coercion ->schema] [[malli/coercion (fn [nom] [:map [nom :string]])]
                               [schema/coercion (fn [nom] {nom s/Str})]
                               [spec/coercion (fn [nom] {nom string?})]]]
    (testing (str coercion)
      (let [app (ring/ring-handler
                 (ring/router
                  [["/parameters"
                    {:post {:description "parameters"
                            :coercion coercion
                            :request {:content {"application/json" {:schema (->schema :b)}
                                                "application/edn" {:schema (->schema :c)}}}
                            :responses {200 {:description "success"
                                             :content {"application/json" {:schema (->schema :ok)}
                                                       "application/edn" {:schema (->schema :edn)}}}}
                            :handler (fn [req]
                                       {:status 200
                                        :body (-> req :parameters :request)})}}]
                   ["/openapi.json"
                    {:get {:handler (openapi/create-openapi-handler)
                           :openapi {:info {:title "" :version "0.0.1"}}
                           :no-doc true}}]]
                  {:validate reitit.ring.spec/validate
                   :data {:middleware [openapi/openapi-feature
                                       rrc/coerce-request-middleware
                                       rrc/coerce-response-middleware]}}))
            spec (-> {:request-method :get
                      :uri "/openapi.json"}
                     app
                     :body)
            spec-coercion (= coercion spec/coercion)]
        (testing "body parameter"
          (is (= (merge {:type "object"
                         :properties {:b {:type "string"}}
                         :required ["b"]}
                        (when-not spec-coercion
                          {:additionalProperties false}))
                 (-> spec
                     (get-in [:paths "/parameters" :post :requestBody :content "application/json" :schema])
                     normalize)))
          (is (= (merge {:type "object"
                         :properties {:c {:type "string"}}
                         :required ["c"]}
                        (when-not spec-coercion
                          {:additionalProperties false}))
                 (-> spec
                     (get-in [:paths "/parameters" :post :requestBody :content "application/edn" :schema])
                     normalize))))
        (testing "body response"
          (is (= (merge {:type "object"
                         :properties {:ok {:type "string"}}
                         :required ["ok"]}
                        (when-not spec-coercion
                          {:additionalProperties false}))
                 (-> spec
                     (get-in [:paths "/parameters" :post :responses 200 :content "application/json" :schema])
                     normalize)))
          (is (= (merge {:type "object"
                         :properties {:edn {:type "string"}}
                         :required ["edn"]}
                        (when-not spec-coercion
                          {:additionalProperties false}))
                 (-> spec
                     (get-in [:paths "/parameters" :post :responses 200 :content "application/edn" :schema])
                     normalize))))
        (testing "validation"
          (let [query {:request-method :post
                       :uri "/parameters"
                       :muuntaja/request {:format "application/json"}
                       :muuntaja/response {:format "application/json"}
                       :body-params {:b "x"}}]
            (testing "of output"
              (is (= {:type :reitit.coercion/response-coercion
                      :in [:response :body]}
                     (try
                       (app query)
                       (catch clojure.lang.ExceptionInfo e
                         (select-keys (ex-data e) [:type :in]))))))
            (testing "of input"
              (is (= {:type :reitit.coercion/request-coercion
                      :in [:request :body-params]}
                     (try
                       (app (assoc query :body-params {:z 1}))
                       (catch clojure.lang.ExceptionInfo e
                         (select-keys (ex-data e) [:type :in]))))))))
        (testing "spec is valid"
          (is (nil? (validate spec))))))))


(deftest default-content-type-test
  (doseq [[coercion ->schema] [[malli/coercion (fn [nom] [:map [nom :string]])]
                               [schema/coercion (fn [nom] {nom s/Str})]
                               [spec/coercion (fn [nom] {nom string?})]]]
    (testing (str coercion)
      (let [app (ring/ring-handler
                 (ring/router
                  [["/explicit-content-type"
                    {:post {:description "parameters"
                            :coercion coercion
                            :request {:content {"application/json" {:schema (->schema :b)}
                                                "application/edn" {:schema (->schema :c)}}}
                            :responses {200 {:description "success"
                                             :content {"application/json" {:schema (->schema :ok)}
                                                       "application/edn" {:schema (->schema :edn)}}}}
                            :handler (fn [req]
                                       {:status 200
                                        :body (-> req :parameters :request)})}}]
                   ["/muuntaja"
                    {:post {:description "default content types from muuntaja"
                            :coercion coercion
                            ;;; TODO: test the :parameters syntax
                            :request {:content {:default {:schema (->schema :b)}
                                                "application/reitit-request" {:schema (->schema :ok)}}}
                            :responses {200 {:description "success"
                                             :content {:default {:schema (->schema :ok)}
                                                       "application/reitit-response" {:schema (->schema :ok)}}}}
                            :handler (fn [req]
                                       {:status 200
                                        :body (-> req :parameters :request)})}}]
                   ["/override-default-content-type"
                    {:post {:description "override default content types from muuntaja"
                            :coercion coercion
                            :openapi/request-content-types ["application/request"]
                            :openapi/response-content-types ["application/response"]
                            ;;; TODO: test the :parameters syntax
                            :request {:content {:default {:schema (->schema :b)}}}
                            :responses {200 {:description "success"
                                             :content {:default {:schema (->schema :ok)}}}}
                            :handler (fn [req]
                                       {:status 200
                                        :body (-> req :parameters :request)})}}]
                   ["/legacy"
                    {:post {:description "default content types from muuntaja, legacy syntax"
                            :coercion coercion
                            ;;; TODO: test the :parameters syntax
                            :request  {:body {:schema (->schema :b)}}
                            :responses {200 {:description "success"
                                             :body {:schema (->schema :ok)}}}
                            :handler (fn [req]
                                       {:status 200
                                        :body (-> req :parameters :request)})}}]
                   ["/form-params"
                    {:post {:description "ring :form-params coercion with :parameters :form syntax"
                            :coercion coercion
                            :parameters {:form (->schema :b)}
                            :handler (fn [req]
                                       {:status 200
                                        :body (-> req :parameters :request)})}}]
                   ["/openapi.json"
                    {:get {:handler (openapi/create-openapi-handler)
                           :openapi {:info {:title "" :version "0.0.1"}}
                           :no-doc true}}]]
                  {:validate reitit.ring.spec/validate
                   :data {:muuntaja (m/create (-> m/default-options
                                                  (update-in [:formats] select-keys ["application/transit+json"])
                                                  (assoc :default-format "application/transit+json")))
                          :middleware [openapi/openapi-feature
                                       rrc/coerce-request-middleware
                                       rrc/coerce-response-middleware]}}))
            spec (-> {:request-method :get
                      :uri "/openapi.json"}
                     app
                     :body)
            spec-coercion (= coercion spec/coercion)]
        (testing "explicit content types"
          (testing "body parameter"
            (is (= ["application/edn" "application/json"]
                   (-> spec
                       (get-in [:paths "/explicit-content-type" :post :requestBody :content])
                       keys
                       sort))))
          (testing "body response"
            (is (= ["application/edn" "application/json"]
                   (-> spec
                       (get-in [:paths "/explicit-content-type" :post :responses 200 :content])
                       keys
                       sort)))))
        (testing "muuntaja content types"
          (testing "body parameter"
            (is (= ["application/transit+json" "application/reitit-request"]
                   (-> spec
                       (get-in [:paths "/muuntaja" :post :requestBody :content])
                       keys))))
          (testing "body response"
            (is (= ["application/transit+json" "application/reitit-response"]
                   (-> spec
                       (get-in [:paths "/muuntaja" :post :responses 200 :content])
                       keys)))))
        (testing "overridden muuntaja content types"
          (testing "body parameter"
            (is (= ["application/request"]
                   (-> spec
                       (get-in [:paths "/override-default-content-type" :post :requestBody :content])
                       keys))))
          (testing "body response"
            (is (= ["application/response"]
                   (-> spec
                       (get-in [:paths "/override-default-content-type" :post :responses 200 :content])
                       keys)))))
        (testing "legacy syntax muuntaja content types"
          (testing "body parameter"
            (is (= ["application/transit+json"]
                   (-> spec
                       (get-in [:paths "/legacy" :post :requestBody :content])
                       keys))))
          (testing "body response"
            (is (= ["application/transit+json"]
                   (-> spec
                       (get-in [:paths "/legacy" :post :responses 200 :content])
                       keys)))))
        (testing ":parameters :form syntax"
          (testing "body parameter"
            (is (= ["application/x-www-form-urlencoded"]
                   (-> spec
                       (get-in [:paths "/form-params" :post :requestBody :content])
                       keys))
                "form parameter schema is put under :requestBody with correct content type")))
        (testing "spec is valid"
          (is (nil? (validate spec))))))))

(deftest recursive-test
  ;; Recursive schemas only properly supported for malli
  ;; See https://github.com/metosin/schema-tools/issues/41
  (let [app (ring/ring-handler
             (ring/router
              [["/parameters"
                {:post {:description "parameters"
                        :coercion malli/coercion
                        :request {:body
                                  [:schema
                                   {:registry {"friend" [:map
                                                         [:age int?]
                                                         [:pet [:ref "pet"]]]
                                               "pet" [:map
                                                      [:name :string]
                                                      [:friends [:vector [:ref "friend"]]]]}}
                                   "friend"]}
                        :handler (fn [req]
                                   {:status 200
                                    :body (-> req :parameters :request)})}}]
               ["/openapi.json"
                {:get {:handler (openapi/create-openapi-handler)
                       :openapi {:info {:title "" :version "0.0.1"}}
                       :no-doc true}}]]
              {:validate reitit.ring.spec/validate
               :data {:middleware [openapi/openapi-feature
                                   rrc/coerce-request-middleware
                                   rrc/coerce-response-middleware]}}))
        spec (-> {:request-method :get
                  :uri "/openapi.json"}
                 app
                 :body)]
    (is (= {:info {:title "" :version "0.0.1"}
            :openapi "3.1.0"
            :x-id #{:reitit.openapi/default}
            :paths {"/parameters"
                    {:post
                     {:description "parameters"
                      :requestBody
                      {:content
                       {"application/json"
                        {:schema {:$ref "#/components/schemas/friend"}}}}}}}
            :components {:schemas {"friend" {:properties {:age {:type "integer"}
                                                          :pet {:$ref "#/components/schemas/pet"}}
                                             :required [:age :pet]
                                             :type "object"}
                                   "pet" {:properties {:friends {:items {:$ref "#/components/schemas/friend"}
                                                                 :type "array"}
                                                       :name {:type "string"}}
                                          :required [:name :friends]
                                          :type "object"}}}}
           spec))
    (testing "spec is valid"
      (is (nil? (validate spec))))))

(def Y :int)
(def Plus [:map
           [:x :int]
           [:y #'Y]])

(deftest openapi-malli-tests
  (let [app (ring/ring-handler
             (ring/router
              [["/openapi.json"
                {:get {:no-doc true
                       :openapi {:info {:title "" :version "0.0.1"}}
                       :handler (openapi/create-openapi-handler)}}]

               ["/malli" {:coercion malli/coercion}
                ["/plus" {:post {:summary "plus with body"
                                 :request {:description "body description"
                                           :content {"application/json" {:schema {:x int?, :y int?}
                                                                         :examples {"1+1" {:value {:x 1, :y 1}}
                                                                                    "1+2" {:value {:x 1, :y 2}}}
                                                                         :openapi {:example {:x 2, :y 2}}}}}
                                 :responses {200 {:description "success"
                                                  :content {"application/json" {:schema {:total int?}
                                                                                :examples {"2" {:value {:total 2}}
                                                                                           "3" {:value {:total 3}}}
                                                                                :openapi {:example {:total 4}}}}}}
                                 :handler (fn [request]
                                            (let [{:keys [x y]} (-> request :parameters :body)]
                                              {:status 200, :body {:total (+ x y)}}))}}]]]

              {:validate reitit.ring.spec/validate
               :data {:middleware [openapi/openapi-feature
                                   rrc/coerce-exceptions-middleware
                                   rrc/coerce-request-middleware
                                   rrc/coerce-response-middleware]}}))
        spec (:body (app {:request-method :get :uri "/openapi.json"}))]
    (is (= {"/malli/plus" {:post {:requestBody {:description "body description",
                                                :content {"application/json" {:schema {:type "object",
                                                                                       :properties {:x {:type "integer"},
                                                                                                    :y {:type "integer"}},
                                                                                       :required [:x :y],
                                                                                       :additionalProperties false},
                                                                              :examples {"1+1" {:value {:x 1, :y 1}}
                                                                                         "1+2" {:value {:x 1, :y 2}}},
                                                                              :example {:x 2, :y 2}}}},
                                  :responses {200 {:description "success",
                                                   :content {"application/json" {:schema {:type "object",
                                                                                          :properties {:total {:type "integer"}},
                                                                                          :required [:total],
                                                                                          :additionalProperties false},
                                                                                 :examples {"2" {:value {:total 2}},
                                                                                            "3" {:value {:total 3}}},
                                                                                 :example {:total 4}}}}},
                                  :summary "plus with body"}}}
           (:paths spec)))
    (is (nil? (validate spec))))
  (testing "ref schemas"
    (let [registry (merge (mc/base-schemas)
                          (mc/type-schemas)
                          {"plus" [:map [:x :int] [:y "y"]]
                           "y" :int})
          app (ring/ring-handler
               (ring/router
                [["/openapi.json"
                  {:get {:no-doc true
                         :openapi {:info {:title "" :version "0.0.1"}}
                         :handler (openapi/create-openapi-handler)}}]
                 ["/post"
                  {:post {:coercion malli/coercion
                          :parameters {:body (mc/schema "plus" {:registry registry})}
                          :handler identity}}]
                 ["/get"
                  {:get {:coercion malli/coercion
                         :parameters {:query (mc/schema "plus" {:registry registry})}
                         :handler identity}}]]))
          spec (:body (app {:request-method :get :uri "/openapi.json"}))]
      (is (= {:openapi "3.1.0"
              :x-id #{:reitit.openapi/default}
              :info {:title "" :version "0.0.1"}
              :paths {"/get" {:get {:parameters [{:in "query"
                                                  :name :x
                                                  :required true
                                                  :schema {:type "integer"}}
                                                 {:in "query"
                                                  :name :y
                                                  :required true
                                                  :schema {:$ref "#/components/schemas/y"}}]}}
                      "/post" {:post
                               {:requestBody
                                {:content
                                 {"application/json"
                                  {:schema
                                   {:$ref "#/components/schemas/plus"}}}}}}}
              :components {:schemas
                           {"y" {:type "integer"}
                            "plus" {:type "object"
                                    :properties {:x {:type "integer"}
                                                 :y {:$ref "#/components/schemas/y"}}
                                    :required [:x :y]}}}}
             spec))
      (is (nil? (validate spec)))))
  (testing "var schemas"
    (let [app (ring/ring-handler
               (ring/router
                [["/openapi.json"
                  {:get {:no-doc true
                         :openapi {:info {:title "" :version "0.0.1"}}
                         :handler (openapi/create-openapi-handler)}}]
                 ["/post"
                  {:post {:coercion malli/coercion
                          :parameters {:body #'Plus}
                          :handler identity}}]
                 ["/get"
                  {:get {:coercion malli/coercion
                         :parameters {:query #'Plus}
                         :handler identity}}]]))
          spec (:body (app {:request-method :get :uri "/openapi.json"}))]
      (is (= {:openapi "3.1.0"
              :x-id #{:reitit.openapi/default}
              :info {:title "" :version "0.0.1"}
              :paths
              {"/post"
               {:post
                {:requestBody
                 {:content
                  {"application/json"
                   {:schema
                    {:$ref "#/components/schemas/reitit.openapi-test~1Plus"}}}}}}
               "/get"
               {:get
                {:parameters
                 [{:in "query" :name :x
                   :required true
                   :schema {:type "integer"}}
                  {:in "query"
                   :name :y
                   :required true
                   :schema {:$ref "#/components/schemas/reitit.openapi-test~1Y"}}]}}}
              :components
              {:schemas
               {"reitit.openapi-test/Plus"
                {:type "object"
                 :properties
                 {:x {:type "integer"}
                  :y {:$ref "#/components/schemas/reitit.openapi-test~1Y"}}
                 :required [:x :y]}
                "reitit.openapi-test/Y" {:type "integer"}}}}
             spec))
      ;; TODO: the OAS 3.1 json schema disallows "/" in :components :schemas keys,
      ;; even though the text of the spec allows it. See:
      ;; https://github.com/seriousme/openapi-schema-validator/blob/772375bf4895f0e641d103c27140cdd1d2afc34e/schemas/v3.1/schema.json#L282
      #_
      (is (nil? (validate spec))))))
