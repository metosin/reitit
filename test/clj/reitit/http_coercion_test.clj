(ns reitit.http-coercion-test
  (:require [clojure.test :refer [deftest testing is]]
            [schema.core :as s]
            [reitit.http :as http]
            [reitit.http.coercion :as rrc]
            [reitit.coercion.spec :as spec]
            [reitit.coercion.schema :as schema]
            [muuntaja.interceptor]
            [jsonista.core :as j]
            [reitit.interceptor.sieppari :as sieppari])
  (:import (clojure.lang ExceptionInfo)
           (java.io ByteArrayInputStream)))

(defn handler [{{{:keys [a]} :query
                 {:keys [b]} :body
                 {:keys [c]} :form
                 {:keys [d]} :header
                 {:keys [e]} :path} :parameters}]
  (if (= 666 a)
    {:status 500
     :body {:evil true}}
    {:status 200
     :body {:total (+ a b c d e)}}))

(def valid-request
  {:uri "/api/plus/5"
   :request-method :get
   :query-params {"a" "1"}
   :body-params {:b 2}
   :form-params {:c 3}
   :headers {"d" "4"}})

(def invalid-request
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
  (let [create (fn [interceptors]
                 (http/ring-handler
                   (http/router
                     ["/api"
                      ["/plus/:e"
                       {:get {:parameters {:query {:a int?}
                                           :body {:b int?}
                                           :form {:c int?}
                                           :header {:d int?}
                                           :path {:e int?}}
                              :responses {200 {:body {:total pos-int?}}
                                          500 {:description "fail"}}
                              :handler handler}}]]
                     {:data {:interceptors interceptors
                             :coercion spec/coercion}})
                   {:executor sieppari/executor}))]

    (testing "without exception handling"
      (let [app (create [(rrc/coerce-request-interceptor)
                         (rrc/coerce-response-interceptor)])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request)))
          (is (= {:status 500
                  :body {:evil true}}
                 (app (assoc-in valid-request [:query-params "a"] "666")))))

        (testing "invalid request"
          (is (thrown-with-msg?
                ExceptionInfo
                #"Request coercion failed"
                (app invalid-request))))

        (testing "invalid response"
          (is (thrown-with-msg?
                ExceptionInfo
                #"Response coercion failed"
                (app invalid-request2))))))

    (testing "with exception handling"
      (let [app (create [(rrc/coerce-exceptions-interceptor)
                         (rrc/coerce-request-interceptor)
                         (rrc/coerce-response-interceptor)])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request))))

        (testing "invalid request"
          (let [{:keys [status]} (app invalid-request)]
            (is (= 400 status))))

        (testing "invalid response"
          (let [{:keys [status]} (app invalid-request2)]
            (is (= 500 status))))))))

(deftest schema-coercion-test
  (let [create (fn [middleware]
                 (http/ring-handler
                   (http/router
                     ["/api"
                      ["/plus/:e"
                       {:get {:parameters {:query {:a s/Int}
                                           :body {:b s/Int}
                                           :form {:c s/Int}
                                           :header {:d s/Int}
                                           :path {:e s/Int}}
                              :responses {200 {:body {:total (s/constrained s/Int pos? 'positive)}}
                                          500 {:description "fail"}}
                              :handler handler}}]]
                     {:data {:interceptors middleware
                             :coercion schema/coercion}})
                   {:executor sieppari/executor}))]

    (testing "without exception handling"
      (let [app (create [(rrc/coerce-request-interceptor)
                         (rrc/coerce-response-interceptor)])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request)))
          (is (= {:status 500
                  :body {:evil true}}
                 (app (assoc-in valid-request [:query-params "a"] "666")))))

        (testing "invalid request"
          (is (thrown-with-msg?
                ExceptionInfo
                #"Request coercion failed"
                (app invalid-request))))

        (testing "invalid response"
          (is (thrown-with-msg?
                ExceptionInfo
                #"Response coercion failed"
                (app invalid-request2))))

        (testing "with exception handling"
          (let [app (create [(rrc/coerce-exceptions-interceptor)
                             (rrc/coerce-request-interceptor)
                             (rrc/coerce-response-interceptor)])]

            (testing "all good"
              (is (= {:status 200
                      :body {:total 15}}
                     (app valid-request))))

            (testing "invalid request"
              (let [{:keys [status]} (app invalid-request)]
                (is (= 400 status))))

            (testing "invalid response"
              (let [{:keys [status]} (app invalid-request2)]
                (is (= 500 status))))))))))

(deftest muuntaja-test
  (let [app (http/ring-handler
              (http/router
                ["/api"
                 ["/plus"
                  {:post {:parameters {:body {:int int?, :keyword keyword?}}
                          :responses {200 {:body {:int int?, :keyword keyword?}}}
                          :handler (fn [{{:keys [body]} :parameters}]
                                     {:status 200
                                      :body body})}}]]
                {:data {:interceptors [(muuntaja.interceptor/format-interceptor)
                                       (rrc/coerce-response-interceptor)
                                       (rrc/coerce-request-interceptor)]
                        :coercion spec/coercion}})
              {:executor sieppari/executor})
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
        (is (= data-json (e2e data-edn)))
        (is (= data-json (e2e data-json)))))

    (testing "edn coercion"
      (let [e2e #(-> (request "application/edn" (pr-str %))
                     (app) :body slurp (read-string))]
        (is (= data-edn (e2e data-edn)))
        (is (thrown? ExceptionInfo (e2e data-json)))))))
