(ns reitit.ring-coercion-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.experimental.lite :as l]
            #?@(:clj [[muuntaja.middleware]
                      [jsonista.core :as j]])
            [malli.core :as m]
            [malli.util :as mu]
            [meta-merge.core :refer [meta-merge]]
            [reitit.coercion.malli :as malli]
            [reitit.coercion.schema :as schema]
            [reitit.coercion.spec :as spec]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [reitit.ring.spec]
            [reitit.ring.coercion :as rrc]
            [schema.core :as s]
            [clojure.spec.alpha]
            [spec-tools.data-spec :as ds])
  #?(:clj
     (:import (clojure.lang ExceptionInfo)
              (java.io ByteArrayInputStream))))

(defn mounted-middleware [app path method]
  (->> app
       (ring/get-router)
       (r/compiled-routes)
       (filter (comp (partial = path) first))
       (first) (last) method :middleware (filter :wrap) (mapv :name)))

(defn handler [{{{:keys [a]} :query
                 {:keys [b]} :body
                 {:keys [c]} :form
                 {:keys [d]} :header
                 {:keys [e]} :path :as parameters} :parameters}]
  ;; extra keys are stripped off
  (assert (every? #{0 1} (map (comp count val) parameters)))

  (if (= 666 a)
    {:status 500
     :body {:evil true}}
    {:status 200
     :body {:total (+ (or a 101) b c d e)}}))

(def valid-request1
  {:uri "/api/plus/5"
   :request-method :get
   :muuntaja/request {:format "application/json"}
   :query-params {"a" "1"}
   :body-params {:b 2}
   :form-params {:c 3}
   :headers {"d" "4"}})

(def valid-request2
  {:uri "/api/plus/5"
   :request-method :get
   :muuntaja/request {:format "application/json"}
   :query-params {}
   :body-params {:b 2}
   :form-params {:c 3}
   :headers {"d" "4"}})

(def valid-request3
  {:uri "/api/plus/5"
   :request-method :get
   :muuntaja/request {:format "application/edn"}
   :query-params {"a" "1", "EXTRA" "VALUE"}
   :body-params {:b 2, :EXTRA "VALUE"}
   :form-params {:c 3, :EXTRA "VALUE"}
   :headers {"d" "4", "EXTRA" "VALUE"}})

(def invalid-request1
  {:uri "/api/plus/5"
   :request-method :get})

(def invalid-request2
  {:uri "/api/plus/5"
   :request-method :get
   :query-params {"a" "1"}
   :body-params {:b 2}
   :form-params {:c 3}
   :headers {"d" "-40"}})

(deftest spec-coercion-test
  (let [create (fn [middleware]
                 (ring/ring-handler
                  (ring/router
                   ["/api"
                    ["/plus/:e"
                     {:get {:parameters {:query {(ds/opt :a) int?}
                                         :body {:b int?}
                                         :form {:c int?}
                                         :header {:d int?}
                                         :path {:e int?}}
                            :responses {200 {:body {:total pos-int?}}
                                        500 {:description "fail"}}
                            :handler handler}}]]
                   {:data {:middleware middleware
                           :coercion spec/coercion}})))]

    (testing "without exception handling"
      (let [app (create [rrc/coerce-request-middleware
                         rrc/coerce-response-middleware])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request1)))
          (is (= {:status 200
                  :body {:total 115}}
                 (app valid-request2)))
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request3)))
          (is (= {:status 500
                  :body {:evil true}}
                 (app (assoc-in valid-request1 [:query-params "a"] "666")))))

        (testing "invalid request"
          (is (thrown-with-msg?
               ExceptionInfo
               #"Request coercion failed"
               (app invalid-request1))))

        (testing "invalid response"
          (is (thrown-with-msg?
               ExceptionInfo
               #"Response coercion failed"
               (app invalid-request2))))))

    (testing "with exception handling"
      (let [app (create [rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request1))))

        (testing "invalid request"
          (let [{:keys [status body]} (app invalid-request1)
                problems (:problems body)]
            (is (= 1 (count problems)))
            (is (= 400 status))))

        (testing "invalid response"
          (let [{:keys [status]} (app invalid-request2)]
            (is (= 500 status))))))))

#?(:clj
(deftest schema-coercion-test
  (let [create (fn [middleware]
                 (ring/ring-handler
                  (ring/router
                   ["/api"
                    ["/plus/:e"
                     {:get {:parameters {:query {(s/optional-key :a) s/Int}
                                         :body {:b s/Int}
                                         :form {:c s/Int}
                                         :header {:d s/Int}
                                         :path {:e s/Int}}
                            :responses {200 {:body {:total (s/constrained s/Int pos? 'positive)}}
                                        500 {:description "fail"}}
                            :handler handler}}]]
                   {:data {:middleware middleware
                           :coercion schema/coercion}})))]

    (testing "withut exception handling"
      (let [app (create [rrc/coerce-request-middleware
                         rrc/coerce-response-middleware])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request1)))
          (is (= {:status 200
                  :body {:total 115}}
                 (app valid-request2)))
          (is (= {:status 500
                  :body {:evil true}}
                 (app (assoc-in valid-request1 [:query-params "a"] "666")))))

        (testing "invalid request"
          (is (thrown-with-msg?
               ExceptionInfo
               #"Request coercion failed"
               (app invalid-request1)))
          (is (thrown-with-msg?
               ExceptionInfo
               #"Request coercion failed"
               (app valid-request3))))

        (testing "invalid response"
          (is (thrown-with-msg?
               ExceptionInfo
               #"Response coercion failed"
               (app invalid-request2))))))

    (testing "with exception handling"
      (let [app (create [rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request1))))

        (testing "invalid request"
          (let [{:keys [status]} (app invalid-request1)]
            (is (= 400 status))))

        (testing "invalid response"
          (let [{:keys [status]} (app invalid-request2)]
            (is (= 500 status)))))))))

(defn- custom-meta-merge-checking-schema
  ([] {})
  ([left] left)
  ([left right]
   (cond
     (and (map? left) (map? right))
     (merge-with custom-meta-merge-checking-schema left right)

     (and (m/schema? left)
          (m/schema? right))
     (mu/merge left right)

     :else
     (meta-merge left right)))
  ([left right & more]
   (reduce custom-meta-merge-checking-schema left (cons right more))))

(defn- custom-meta-merge-checking-parameters
  ([] {})
  ([left] left)
  ([left right]
   (let [pleft (-> left :parameters :path)
         pright (-> right :parameters :path)]
     (if (and (map? left) (map? right) pleft pright)
       (-> (merge-with custom-meta-merge-checking-parameters left right)
           (assoc-in [:parameters :path] (reduce mu/merge (concat pleft pright))))
       (meta-merge left right))))
  ([left right & more]
   (reduce custom-meta-merge-checking-parameters left (cons right more))))

(deftest malli-coercion-test
  (let [create (fn [middleware routes]
                 (ring/ring-handler
                  (ring/router
                   routes
                   {:data {:middleware middleware
                           :coercion malli/coercion}})))]

    (doseq [{:keys [style routes]} [{:style "malli"
                                     :routes ["/api"
                                              ["/validate" {:summary "just validation"
                                                            :coercion (reitit.coercion.malli/create {:transformers {}})
                                                            :post {:parameters {:body [:map [:x int?]]}
                                                                   :responses {200 {:body [:map [:x int?]]}}
                                                                   :handler (fn [req]
                                                                              {:status 200
                                                                               :body (-> req :parameters :body)})}}]

                                              ["/no-op" {:summary "no-operation"
                                                         :coercion (reitit.coercion.malli/create {:transformers {}, :validate false})
                                                         :post {:parameters {:body [:map [:x int?]]}
                                                                :responses {200 {:body [:map [:x int?]]}}
                                                                :handler (fn [req]
                                                                           {:status 200
                                                                            :body (-> req :parameters :body)})}}]

                                              ["/skip" {:summary "skip"
                                                        :coercion (reitit.coercion.malli/create {:enabled false})
                                                        :post {:parameters {:body [:map [:x int?]]}
                                                               :responses {200 {:body [:map [:x int?]]}}
                                                               :handler (fn [req]
                                                                          {:status 200
                                                                           :body (-> req :parameters :body)})}}]

                                              ["/or" {:post {:summary "accepts either of two map schemas"
                                                             :parameters {:body [:or [:map [:x int?]] [:map [:y int?]]]}
                                                             :responses {200 {:body [:map [:msg string?]]}}
                                                             :handler (fn [{{{:keys [x]} :body} :parameters}]
                                                                        {:status 200
                                                                         :body {:msg (if x "you sent x" "you sent y")}})}}]

                                              ["/plus/:e" {:get {:parameters {:query [:map [:a {:optional true} int?]]
                                                                              :body [:map [:b int?]]
                                                                              :form [:map [:c [int? {:default 3}]]]
                                                                              :header [:map [:d int?]]
                                                                              :path [:map [:e int?]]}
                                                                 :responses {200 {:body [:map [:total pos-int?]]}
                                                                             500 {:description "fail"}}
                                                                 :handler handler}}]]}
                                    {:style "lite"
                                     :routes ["/api"

                                              ["/validate" {:summary "just validation"
                                                            :coercion (reitit.coercion.malli/create {:transformers {}})
                                                            :post {:parameters {:body {:x int?}}
                                                                   :responses {200 {:body {:x int?}}}
                                                                   :handler (fn [req]
                                                                              {:status 200
                                                                               :body (-> req :parameters :body)})}}]

                                              ["/no-op" {:summary "no-operation"
                                                         :coercion (reitit.coercion.malli/create {:transformers {}, :validate false})
                                                         :post {:parameters {:body {:x int?}}
                                                                :responses {200 {:body {:x int?}}}
                                                                :handler (fn [req]
                                                                           {:status 200
                                                                            :body (-> req :parameters :body)})}}]

                                              ["/skip" {:summary "skip"
                                                        :coercion (reitit.coercion.malli/create {:enabled false})
                                                        :post {:parameters {:body {:x int?}}
                                                               :responses {200 {:body {:x int?}}}
                                                               :handler (fn [req]
                                                                          {:status 200
                                                                           :body (-> req :parameters :body)})}}]

                                              ["/or" {:post {:summary "accepts either of two map schemas"
                                                             :parameters {:body (l/or {:x int?} {:y int?})}
                                                             :responses {200 {:body {:msg string?}}}
                                                             :handler (fn [{{{:keys [x]} :body} :parameters}]
                                                                        {:status 200
                                                                         :body {:msg (if x "you sent x" "you sent y")}})}}]

                                              ["/plus/:e" {:get {:parameters {:query {:a (l/optional int?)}
                                                                              :body {:b int?}
                                                                              :form {:c [int? {:default 3}]}
                                                                              :header {:d int?}
                                                                              :path {:e int?}}
                                                                 :responses {200 {:body {:total pos-int?}}
                                                                             500 {:description "fail"}}
                                                                 :handler handler}}]]}]]

      (testing (str "malli with style " style)

        (testing "without exception handling"
          (let [app (create [rrc/coerce-request-middleware
                             rrc/coerce-response-middleware] routes)]

            (testing "all good"
              (is (= {:status 200
                      :body {:total 15}}
                     (app valid-request1)))
              (is (= {:status 200
                      :body {:total 115}}
                     (app valid-request2)))
              (is (= {:status 200
                      :body {:total 15}}
                     (app valid-request3)))
              (testing "default values work"
                (is (= {:status 200
                        :body {:total 15}}
                       (app (update valid-request3 :form-params dissoc :c)))))
              (is (= {:status 500
                      :body {:evil true}}
                     (app (assoc-in valid-request1 [:query-params "a"] "666")))))

            (testing "invalid request"
              (is (thrown-with-msg?
                   ExceptionInfo
                   #"Request coercion failed"
                   (app invalid-request1))))

            (testing "invalid response"
              (is (thrown-with-msg?
                   ExceptionInfo
                   #"Response coercion failed"
                   (app invalid-request2))))))

        (testing "with exception handling"
          (let [app (create [rrc/coerce-exceptions-middleware
                             rrc/coerce-request-middleware
                             rrc/coerce-response-middleware] routes)]

            (testing "just validation"
              (is (= 400 (:status (app {:uri "/api/validate"
                                        :request-method :post
                                        :muuntaja/request {:format "application/edn"}
                                        :body-params 123}))))
              (is (= [:reitit.ring.coercion/coerce-exceptions
                      :reitit.ring.coercion/coerce-request
                      :reitit.ring.coercion/coerce-response]
                     (mounted-middleware app "/api/validate" :post))))

            (testing "no tranformation & validation"
              (is (= 123 (:body (app {:uri "/api/no-op"
                                      :request-method :post
                                      :muuntaja/request {:format "application/edn"}
                                      :body-params 123}))))
              (is (= [:reitit.ring.coercion/coerce-exceptions
                      :reitit.ring.coercion/coerce-request
                      :reitit.ring.coercion/coerce-response]
                     (mounted-middleware app "/api/no-op" :post))))

            (testing "skipping coercion"
              (is (= nil (:body (app {:uri "/api/skip"
                                      :request-method :post
                                      :muuntaja/request {:format "application/edn"}
                                      :body-params 123}))))
              (is (= [:reitit.ring.coercion/coerce-exceptions]
                     (mounted-middleware app "/api/skip" :post))))

            (testing "or #407"
              (is (= {:status 200
                      :body {:msg "you sent x"}}
                     (app {:uri "/api/or"
                           :request-method :post
                           :body-params {:x 1}}))))

            (testing "all good"
              (is (= {:status 200
                      :body {:total 15}}
                     (app valid-request1))))

            (testing "invalid request"
              (let [{:keys [status]} (app invalid-request1)]
                (is (= 400 status))))

            (testing "invalid response"
              (let [{:keys [status]} (app invalid-request2)]
                (is (= 500 status))))))))

    (testing "open & closed schemas"
      (let [endpoint (fn [schema]
                       {:get {:parameters {:body schema}
                              :responses {200 {:body schema}}
                              :handler (fn [{{:keys [body]} :parameters}]
                                         {:status 200, :body (assoc body :response true)})}})
            ->app (fn [options]
                    (ring/ring-handler
                     (ring/router
                      ["/api"
                       ["/default" (endpoint [:map [:x int?]])]
                       ["/closed" (endpoint [:map {:closed true} [:x int?]])]
                       ["/open" (endpoint [:map {:closed false} [:x int?]])]]
                      {:data {:middleware [rrc/coerce-exceptions-middleware
                                           rrc/coerce-request-middleware
                                           rrc/coerce-response-middleware]
                              :coercion (malli/create options)}})))
            ->request (fn [uri] {:uri (str "/api/" uri)
                                 :request-method :get
                                 :muuntaja/request {:format "application/json"}
                                 :body-params {:x 1, :request true}})]

        (testing "with defaults"
          (let [app (->app nil)]

            (testing "default: keys are stripped"
              (is (= {:status 200, :body {:x 1}}
                     (app (->request "default")))))

            (testing "closed: keys are stripped"
              (is (= {:status 200, :body {:x 1}}
                     (app (->request "closed")))))

            (testing "open: keys are NOT stripped"
              (is (= {:status 200, :body {:x 1, :request true, :response true}}
                     (app (->request "open")))))))

        (testing "when schemas are not closed"
          (let [app (->app {:compile (fn [v _] v)})]

            (testing "default: keys are stripped"
              (is (= {:status 200, :body {:x 1}}
                     (app (->request "default")))))

            (testing "closed: keys are stripped"
              (is (= {:status 200, :body {:x 1}}
                     (app (->request "closed")))))

            (testing "open: keys are NOT stripped"
              (is (= {:status 200, :body {:x 1, :request true, :response true}}
                     (app (->request "open")))))))

        (testing "encoding errors"
          (let [app (->app {:encode-error (fn [error] {:errors (:humanized error)})})]
            (is (= {:status 400, :headers {}, :body {:errors {:x ["missing required key"]}}}
                   (app (assoc (->request "closed") :body-params {}))))))

        (testing "when schemas are not closed and extra keys are not stripped"
          (let [app (->app {:compile (fn [v _] v) :strip-extra-keys false})]
            (testing "default: keys are NOT stripped"
              (is (= {:status 200, :body {:x 1, :request true, :response true}}
                     (app (->request "default")))))

            (testing "closed: FAILS for extra keys"
              (is (= 400 (:status (app (->request "closed"))))))

            (testing "open: keys are NOT stripped"
              (is (= {:status 200, :body {:x 1, :request true, :response true}}
                     (app (->request "open")))))))))

    (testing "sequence schemas"
      (let [app (ring/ring-handler
                 (ring/router
                  ["/ping" {:get {:parameters {:body [:vector [:map [:message string?]]]}
                                  :responses {200 {:body [:vector [:map [:pong string?]]]}
                                              501 {:body [:vector [:map [:error string?]]]}}
                                  :handler (fn [{{[{:keys [message]}] :body} :parameters :as req}]
                                             (condp = message
                                               "ping" {:status 200
                                                       :body [{:pong message}]}
                                               "fail" {:status 501
                                                       :body [{:error "fail"}]}
                                               {:status 200
                                                :body {:invalid "response"}}))}}]
                  {:data {:middleware [rrc/coerce-exceptions-middleware
                                       rrc/coerce-request-middleware
                                       rrc/coerce-response-middleware]
                          :coercion malli/coercion}}))
            ->request (fn [body]
                        {:uri "/ping"
                         :request-method :get
                         :muuntaja/request {:format "application/json"}
                         :body-params body})]

        (testing "succesfull request"
          (let [{:keys [status body]} (app (->request [{:message "ping"}]))]
            (is (= 200 status))
            (is (= [{:pong "ping"}] body)))

          (testing "succesfull failure"
            (let [{:keys [status body]} (app (->request [{:message "fail"}]))]
              (is (= 501 status))
              (is (= [{:error "fail"}] body))))

          (testing "failed response"
            (let [{:keys [status body]} (app (->request [{:message "kosh"}]))]
              (is (= 500 status))
              (is (= :reitit.coercion/response-coercion (:type body))))))))

    (testing "encoding responses"
      (let [->app (fn [total-schema]
                    (ring/ring-handler
                     (ring/router
                      ["/total" {:get {:parameters {:query [:map [:x :int]]}
                                       :responses {200 {:body [:map [:total total-schema]]}}
                                       :handler (fn [{{{:keys [x]} :query} :parameters}]
                                                  {:status 200
                                                   :body {:total (* x x)}})}}]
                      {:data {:middleware [rrc/coerce-request-middleware
                                           rrc/coerce-response-middleware]
                              :coercion malli/coercion}})))
            call (fn [accept total-schema]
                   ((->app total-schema) {:uri "/total"
                                          :request-method :get
                                          :muuntaja/request {:format "application/json"}
                                          :muuntaja/response {:format accept}
                                          :query-params {"x" "2"}}))]

        (testing "no encoding"
          (is (= {:status 200, :body {:total +4}} (call "application/json" :int))))

        (testing "json encoding"
          (is (= {:status 200, :body {:total -4}} (call "application/json" [:int {:encode/json -}]))))

        (testing "edn encoding (nada)"
          (is (= {:status 200, :body {:total +4}} (call "application/edn" [:int {:encode/json -}]))))))

    (testing "using custom meta-merge function"
      (let [->app (fn [schema-fn meta-merge]
                    (ring/ring-handler
                     (ring/router
                      ["/merging-params/:foo" {:parameters {:path (schema-fn [:map [:foo :string]])}}
                       ["/:bar" {:parameters {:path (schema-fn [:map [:bar :string]])}
                                 :get {:handler (fn [{{{:keys [foo bar]} :path} :parameters}]
                                                  {:status 200
                                                   :body {:total (str "FOO: " foo ", "
                                                                      "BAR: " bar)}})}}]]
                      {:data {:middleware [rrc/coerce-request-middleware
                                           rrc/coerce-response-middleware]
                              :coercion malli/coercion}
                       :meta-merge meta-merge})))
            call (fn [schema-fn meta-merge]
                   ((->app schema-fn meta-merge) {:uri "/merging-params/this/that"
                                                  :request-method :get}))]

        (is (= {:status 200, :body {:total "FOO: this, BAR: that"}} (call m/schema custom-meta-merge-checking-schema)))
        (is (= {:status 200, :body {:total "FOO: this, BAR: that"}} (call identity custom-meta-merge-checking-parameters)))))))

#?(:clj
(deftest per-content-type-test
  (doseq [[coercion json-request edn-request default-request json-response edn-response default-response]
          [[malli/coercion
            [:map [:request [:enum :json]] [:response any?]]
            [:map [:request [:enum :edn]] [:response any?]]
            [:map [:request [:enum :default]] [:response any?]]
            [:map [:request any?] [:response [:enum :json]]]
            [:map [:request any?] [:response [:enum :edn]]]
            [:map [:request any?] [:response [:enum :default]]]]
           [schema/coercion
            {:request (s/eq :json) :response s/Any}
            {:request (s/eq :edn) :response s/Any}
            {:request (s/eq :default) :response s/Any}
            {:request s/Any :response (s/eq :json)}
            {:request s/Any :response (s/eq :edn)}
            {:request s/Any :response (s/eq :default)}]
           [spec/coercion
            {:request (clojure.spec.alpha/spec #{:json}) :response any?}
            {:request (clojure.spec.alpha/spec #{:edn}) :response any?}
            {:request (clojure.spec.alpha/spec #{:default}) :response any?}
            {:request any? :response (clojure.spec.alpha/spec #{:json})}
            {:request any? :response (clojure.spec.alpha/spec #{:end})}
            {:request any? :response (clojure.spec.alpha/spec #{:default})}]]]
    (testing (str coercion)
      (doseq [{:keys [name app]}
              [{:name "using top-level :body"
                :app (ring/ring-handler
                      (ring/router
                       ["/foo" {:post {:request {:content {"application/json" {:schema json-request}
                                                           "application/edn" {:schema edn-request}}
                                                 :body default-request}
                                       :responses {200 {:content {"application/json" {:schema json-response}
                                                                  "application/edn" {:schema edn-response}}
                                                        :body default-response}}
                                       :handler (fn [req]
                                                  {:status 200
                                                   :body (-> req :parameters :request)})}}]
                       {:validate reitit.ring.spec/validate
                        :data {:middleware [rrc/coerce-request-middleware
                                            rrc/coerce-response-middleware]
                               :coercion coercion}}))}
               {:name "using :default content"
                :app (ring/ring-handler
                      (ring/router
                       ["/foo" {:post {:request {:content {"application/json" {:schema json-request}
                                                           "application/edn" {:schema edn-request}
                                                           :default {:schema default-request}}
                                                 :body json-request} ;; not applied as :default exists
                                       :responses {200 {:content {"application/json" {:schema json-response}
                                                                  "application/edn" {:schema edn-response}
                                                                  :default {:schema default-response}}
                                                        :body json-response}} ;; not applied as :default exists
                                       :handler (fn [req]
                                                  {:status 200
                                                   :body (-> req :parameters :request)})}}]
                       {:validate reitit.ring.spec/validate
                        :data {:middleware [rrc/coerce-request-middleware
                                            rrc/coerce-response-middleware]
                               :coercion coercion}}))}]]
        (testing name
          (let [call (fn [request]
                       (try
                         (app request)
                         (catch ExceptionInfo e
                           (select-keys (ex-data e) [:type :in]))))
                request (fn [request-format response-format body]
                          {:request-method :post
                           :uri "/foo"
                           :muuntaja/request {:format request-format}
                           :muuntaja/response {:format response-format}
                           :body-params body})
                normalize-json (fn[body]
                                 (-> body j/write-value-as-string (j/read-value j/keyword-keys-object-mapper)))]
            (testing "succesful call"
              (is (= {:status 200 :body {:request "json", :response "json"}}
                     (normalize-json (call (request "application/json" "application/json" {:request :json :response :json})))))
              (is (= {:status 200 :body {:request "edn", :response "json"}}
                     (normalize-json (call (request "application/edn" "application/json" {:request :edn :response :json})))))
              (is (= {:status 200 :body {:request :default, :response :default}}
                     (call (request "application/transit" "application/transit" {:request :default :response :default})))))
            (testing "request validation fails"
              (is (= {:type :reitit.coercion/request-coercion :in [:request :body-params]}
                     (call (request "application/edn" "application/json" {:request :json :response :json}))))
              (is (= {:type :reitit.coercion/request-coercion :in [:request :body-params]}
                     (call (request "application/json" "application/json" {:request :edn :response :json}))))
              (is (= {:type :reitit.coercion/request-coercion :in [:request :body-params]}
                     (call (request "application/transit" "application/json" {:request :edn :response :json})))))
            (testing "response validation fails"
              (is (= {:type :reitit.coercion/response-coercion :in [:response :body]}
                     (call (request "application/json" "application/json" {:request :json :response :edn}))))
              (is (= {:type :reitit.coercion/response-coercion :in [:response :body]}
                     (call (request "application/json" "application/edn" {:request :json :response :json}))))
              (is (= {:type :reitit.coercion/response-coercion :in [:response :body]}
                     (call (request "application/json" "application/transit" {:request :json :response :json}))))))))))))


#?(:clj
   (deftest response-coercion-test
     (doseq [[coercion schema-200 schema-default]
             [[malli/coercion
               [:map [:a :int]]
               [:map [:b :int]]]
              [schema/coercion
               {:a s/Int}
               {:b s/Int}]
              [spec/coercion
               {:a int?}
               {:b int?}]]]
       (testing (str coercion)
         (let [app (ring/ring-handler
                    (ring/router
                     [["/foo" {:post {:responses {200 {:content {:default {:schema schema-200}}}
                                                  201 {:content {"application/edn" {:schema schema-200}}}
                                                  202 {:description "status code and content-type explicitly mentioned, but no :schema"
                                                       :content {"application/edn" {}
                                                                 "application/json" {}}}
                                                  :default {:content {"application/json" {:schema schema-default}}}}
                                      :handler (fn [req]
                                                 {:status (-> req :body-params :status)
                                                  :body (-> req :body-params :response)})}}]
                      ["/bar" {:post {:responses {200 {:content {:default {:schema schema-200}}}}
                                      :handler (fn [req]
                                                 {:status (-> req :body-params :status)
                                                  :body (-> req :body-params :response)})}}]
                      ["/quux" {:post {:handler (fn [req]
                                                  {:status (-> req :body-params :status)
                                                   :body (-> req :body-params :response)})}}]]
                     {:validate reitit.ring.spec/validate
                      :data {:middleware [rrc/coerce-request-middleware
                                          rrc/coerce-response-middleware]
                             :coercion coercion}}))
               call (fn [request]
                      (try
                        (app request)
                        (catch ExceptionInfo e
                          (select-keys (ex-data e) [:type :in]))))
               request (fn [uri body]
                         {:request-method :post
                          :uri uri
                          :muuntaja/request {:format "application/json"}
                          :muuntaja/response {:format (:format body "application/json")}
                          :body-params body})]
           (testing "explicit response schema"
             (is (= {:status 200 :body {:a 1}}
                    (call (request "/foo" {:status 200 :response {:a 1}})))
                 "valid response")
             (is (= {:type :reitit.coercion/response-coercion, :in [:response :body]}
                    (call (request "/foo" {:status 200 :response {:b 1}})))
                 "invalid response")
             (is (= {:type :reitit.coercion/response-coercion, :in [:response :body]}
                    (call (request "/foo" {:status 200 :response {:b 1} :format "application/edn"})))
                 "invalid response, different content-type"))
           (testing "explicit response schema, but for the wrong content-type"
             (is (= {:status 201 :body "anything goes!"}
                    (call (request "/foo" {:status 201 :response "anything goes!"})))
                 "no coercion applied"))
           (testing "response config without :schema"
             (is (= {:status 202 :body "anything goes!"}
                    (call (request "/foo" {:status 202 :response "anything goes!"})))
                 "no coercion applied"))
           (testing "default response schema"
             (is (= {:status 300 :body {:b 2}}
                    (call (request "/foo" {:status 300 :response {:b 2}})))
                 "valid response")
             (is (= {:type :reitit.coercion/response-coercion, :in [:response :body]}
                    (call (request "/foo" {:status 300 :response {:a 2}})))
                 "invalid response")
             (is (= {:status 300 :body "anything goes!"}
                    (call (request "/foo" {:status 300 :response "anything goes!" :format "application/edn"})))
                 "no coercion applied due to content-type"))
           (testing "no default"
             (is (= {:status 200 :body {:a 1}}
                    (call (request "/bar" {:status 200 :response {:a 1}})))
                 "valid response")
             (testing "unlisted response code"
               (is (= {:status 202 :body "anything goes!"}
                      (call (request "/bar" {:status 202 :response "anything goes!"})))
                   "no coercion applied")))
           (testing "no response coercion"
             (is (= {:status 200 :body "anything goes!"}
                      (call (request "/quux" {:status 200 :response "anything goes!"})))
                   "no coercion applied")))))))

#?(:clj
   (deftest muuntaja-test
     (let [app (ring/ring-handler
                (ring/router
                 ["/api"
                  ["/plus"
                   {:post {:parameters {:body {:int int?, :keyword keyword?}}
                           :responses {200 {:body {:int int?, :keyword keyword?}}}
                           :handler (fn [{{:keys [body]} :parameters}]
                                      {:status 200
                                       :body body})}}]]
                 {:data {:middleware [muuntaja.middleware/wrap-format
                                      rrc/coerce-request-middleware
                                      rrc/coerce-response-middleware]
                         :coercion spec/coercion}}))
           request (fn [content-type body]
                     (-> {:request-method :post
                          :headers {"content-type" content-type, "accept" content-type}
                          :uri "/api/plus"
                          :body body}))
           data-edn {:int 1 :keyword :kikka}
           data-json {:int 1 :keyword "kikka"}]

       (testing "json coercion"
         (let [e2e #(-> (request "application/json" (ByteArrayInputStream. (j/write-value-as-bytes %)))
                        (app) :body (slurp) (j/read-value (j/object-mapper {:decode-key-fn true})))]
           (is (= data-json (e2e (assoc data-edn :EXTRA "VALUE"))))
           (is (= data-json (e2e (assoc data-json :EXTRA "VALUE"))))))

       (testing "edn coercion"
         (let [e2e #(-> (request "application/edn" (pr-str %))
                        (app) :body slurp (read-string))]
           (is (= data-edn (e2e (assoc data-edn :EXTRA "VALUE"))))
           (is (thrown? ExceptionInfo (e2e data-json))))))))
