(ns reitit.openapi-test
  (:require [clojure.java.shell :as shell]
            [clojure.test :refer [deftest is testing]]
            [jsonista.core :as j]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as matchers]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as malli]
            [reitit.coercion.schema :as schema]
            [reitit.coercion.spec :as spec]
            [reitit.http.interceptors.multipart]
            [reitit.openapi :as openapi]
            [reitit.ring :as ring]
            [reitit.ring.spec]
            [reitit.ring.coercion :as rrc]
            [reitit.swagger-ui :as swagger-ui]
            [schema.core :as s]
            [schema-tools.core]
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
                               500 {:description "fail"}}
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
                             500 {:description "fail"}}
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
                             500 {:description "fail"}}
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
                                                                      :description ""
                                                                      :required true
                                                                      :schema {:type "integer"
                                                                               :format "int64"}}
                                                                     {:in "query"
                                                                      :name "y"
                                                                      :description ""
                                                                      :required true
                                                                      :schema {:type "integer"
                                                                               :format "int64"}}
                                                                     {:in "path"
                                                                      :name "z"
                                                                      :description ""
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
                                                                       :description ""
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
                                                                         :description ""
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
                                                                       500 {:description "fail"}}
                                                           :summary "plus with body"}}}}]
      (is (= expected spec))
      (is (nil? (validate spec))))))

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

(defn- normalize
  "Normalize format of openapi spec by converting it to json and back.
  Handles differences like :q vs \"q\" in openapi generation."
  [data]
  (-> data
      j/write-value-as-string
      (j/read-value j/keyword-keys-object-mapper)))

(deftest all-parameter-types-test
  (doseq [[coercion ->schema]
          [[#'malli/coercion (fn [nom] [:map [nom :string]])]
           [#'schema/coercion (fn [nom] {nom s/Str})]
           [#'spec/coercion (fn [nom] {nom string?})]]]
    (testing coercion
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
                        :schema {:type "string"}}
                       {:in "header"
                        :name "h"
                        :required true
                        :schema {:type "string"}}
                       {:in "cookie"
                        :name "c"
                        :required true
                        :schema {:type "string"}}
                       {:in "path"
                        :name "p"
                        :required true
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

(deftest multipart-test
  (doseq [[coercion file-schema]
          [[#'malli/coercion [:map {:json-schema {:type "string"
                                                  :format "binary"}}
                              [:filename :string]
                              [:content-type :string]
                              [:bytes :int]]]
           [#'schema/coercion (schema-tools.core/schema {:filename s/Str
                                                         :content-type s/Str
                                                         :bytes s/Num}
                                                        {:openapi {:type "string"
                                                                   :format "binary"}})]
           [#'spec/coercion reitit.http.interceptors.multipart/bytes-part]]]
    (testing coercion
      (let [app (ring/ring-handler
                 (ring/router
                  [["/upload"
                    {:post {:decription "upload"
                            :coercion @coercion
                            :parameters {:multipart {:file file-schema}}
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
                                             :format "binary"}}
                         :required ["file"]}
                        (when-not (= #'spec/coercion coercion)
                          {:additionalProperties false}))
                 (-> spec
                     (get-in [:paths "/upload" :post :requestBody :content "multipart/form-data" :schema])
                     normalize))))
        (testing "spec is valid"
          (is (nil? (validate spec))))))))

(deftest per-content-type-test
  (doseq [[coercion ->schema]
          [[#'malli/coercion (fn [nom] [:map [nom :string]])]
           [#'schema/coercion (fn [nom] {nom s/Str})]
           [#'spec/coercion (fn [nom] {nom string?})]]]
    (testing coercion
      (let [app (ring/ring-handler
                 (ring/router
                  [["/parameters"
                    {:post {:description "parameters"
                            :coercion @coercion
                            :parameters {:request {:content {"application/json" (->schema :b)
                                                             "application/edn" (->schema :c)}}}
                            :responses {200 {:description "success"
                                             :content {"application/json" (->schema :ok)
                                                       "application/edn" (->schema :edn)}}}
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
        (testing "body parameter"
          (is (match? (merge {:type "object"
                              :properties {:b {:type "string"}}
                              :required ["b"]}
                             (when-not (#{#'spec/coercion} coercion)
                               {:additionalProperties false}))
                      (-> spec
                          (get-in [:paths "/parameters" :post :requestBody :content "application/json" :schema])
                          normalize)))
          (is (match? (merge {:type "object"
                              :properties {:c {:type "string"}}
                              :required ["c"]}
                             (when-not (#{#'spec/coercion} coercion)
                               {:additionalProperties false}))
                      (-> spec
                          (get-in [:paths "/parameters" :post :requestBody :content "application/edn" :schema])
                          normalize))))
        (testing "body response"
          (is (match? (merge {:type "object"
                              :properties {:ok {:type "string"}}
                              :required ["ok"]}
                             (when-not (#{#'spec/coercion} coercion)
                               {:additionalProperties false}))
                      (-> spec
                          (get-in [:paths "/parameters" :post :responses 200 :content "application/json" :schema])
                          normalize)))
          (is (match? (merge {:type "object"
                              :properties {:edn {:type "string"}}
                              :required ["edn"]}
                             (when-not (#{#'spec/coercion} coercion)
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
  (doseq [[coercion ->schema]
          [[#'malli/coercion (fn [nom] [:map [nom :string]])]
           [#'schema/coercion (fn [nom] {nom s/Str})]
           [#'spec/coercion (fn [nom] {nom string?})]]]
    (testing coercion
      (doseq [content-type ["application/json" "application/edn"]]
        (testing (str "default content type " content-type)
          (let [app (ring/ring-handler
                     (ring/router
                      [["/parameters"
                        {:post {:description "parameters"
                                :coercion @coercion
                                :content-types [content-type] ;; TODO should this be under :openapi ?
                                :parameters {:request {:content {"application/transit" (->schema :transit)}
                                                       :body (->schema :default)}}
                                :responses {200 {:description "success"
                                                 :content {"application/transit" (->schema :transit)}
                                                 :body (->schema :default)}}
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
            (testing "body parameter"
              (is (match? (matchers/in-any-order [content-type "application/transit"])
                          (-> spec
                              (get-in [:paths "/parameters" :post :requestBody :content])
                              keys))))
            (testing "body response"
              (is (match? (matchers/in-any-order [content-type "application/transit"])
                          (-> spec
                              (get-in [:paths "/parameters" :post :responses 200 :content])
                              keys))))
            (testing "spec is valid"
              (is (nil? (validate spec))))))))))
