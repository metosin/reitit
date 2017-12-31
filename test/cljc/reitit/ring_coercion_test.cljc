(ns reitit.ring-coercion-test
  (:require [clojure.test :refer [deftest testing is]]
            [schema.core :as s]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec :as spec]
            [reitit.coercion.schema :as schema])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defn handler [{{{:keys [a]} :query
                 {:keys [b]} :body
                 {:keys [c]} :form
                 {:keys [d]} :header
                 {:keys [e]} :path} :parameters}]
  {:status 200
   :body {:total (+ a b c d e)}})

(def valid-request
  {:uri "/api/plus/5"
   :request-method :get
   :query-params {"a" "1"}
   :body-params {:b 2}
   :form-params {:c 3}
   :header-params {:d 4}})

(def invalid-request
  {:uri "/api/plus/5"
   :request-method :get})

(def invalid-request2
  {:uri "/api/plus/5"
   :request-method :get
   :query-params {"a" "1"}
   :body-params {:b 2}
   :form-params {:c 3}
   :header-params {:d -40}})

(deftest spec-coercion-test
  (let [create (fn [middleware]
                 (ring/ring-handler
                   (ring/router
                     ["/api"
                      ["/plus/:e"
                       {:get {:parameters {:query {:a int?}
                                           :body {:b int?}
                                           :form {:c int?}
                                           :header {:d int?}
                                           :path {:e int?}}
                              :responses {200 {:schema {:total pos-int?}}}
                              :handler handler}}]]
                     {:data {:middleware middleware
                             :coercion spec/coercion}})))]

    (testing "withut exception handling"
      (let [app (create [rrc/coerce-request-middleware
                         rrc/coerce-response-middleware])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request))))

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
      (let [app (create [rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request))))

        (testing "invalid request"
          (let [{:keys [status body]} (app invalid-request)]
            (is (= 400 status))))

        (testing "invalid response"
          (let [{:keys [status body]} (app invalid-request2)]
            (is (= 500 status))))))))

(deftest schema-coercion-test
  (let [create (fn [middleware]
                 (ring/ring-handler
                   (ring/router
                     ["/api"
                      ["/plus/:e"
                       {:get {:parameters {:query {:a s/Int}
                                           :body {:b s/Int}
                                           :form {:c s/Int}
                                           :header {:d s/Int}
                                           :path {:e s/Int}}
                              :responses {200 {:schema {:total (s/constrained s/Int pos? 'positive)}}}
                              :handler handler}}]]
                     {:data {:middleware middleware
                             :coercion schema/coercion}})))]

    (testing "withut exception handling"
      (let [app (create [rrc/coerce-request-middleware
                         rrc/coerce-response-middleware])]

        (testing "all good"
          (is (= {:status 200
                  :body {:total 15}}
                 (app valid-request))))

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
          (let [app (create [rrc/coerce-exceptions-middleware
                             rrc/coerce-request-middleware
                             rrc/coerce-response-middleware])]

            (testing "all good"
              (is (= {:status 200
                      :body {:total 15}}
                     (app valid-request))))

            (testing "invalid request"
              (let [{:keys [status body]} (app invalid-request)]
                (is (= 400 status))))

            (testing "invalid response"
              (let [{:keys [status body]} (app invalid-request2)]
                (is (= 500 status))))))))))
