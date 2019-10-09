(ns reitit.ring-spec-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.ring.spec :as rrs]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec]
            [clojure.spec.alpha :as s]
            [reitit.core :as r])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(s/def ::role #{:admin :user})
(s/def ::roles (s/and (s/coll-of ::role :into #{}) set?))

(deftest route-data-validation-test
  (testing "validation is turned off by default"
    (is (r/router?
          (r/router
            ["/api" {:handler "identity"}]))))

  (testing "with default spec validates :name, :handler and :middleware"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid route data"
          (ring/router
            ["/api" {:handler "identity"}]
            {:validate rrs/validate})))
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid route data"
          (ring/router
            ["/api" {:handler identity
                     :name "kikka"}]
            {:validate rrs/validate}))))

  (testing "all endpoints are validated"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid route data"
          (ring/router
            ["/api" {:patch {:handler "identity"}}]
            {:validate rrs/validate}))))

  (testing "spec can be overridden"
    (is (r/router?
          (ring/router
            ["/api" {:handler "identity"}]
            {:spec (s/spec any?)
             :validate rrs/validate})))

    (testing "predicates are not allowed"
      (is (thrown-with-msg?
            ExceptionInfo
            #":reitit.ring.spec/invalid-specs"
            (ring/router
              ["/api" {:handler "identity"}]
              {:spec any?
               :validate rrs/validate})))))

  (testing "middleware can contribute to specs"
    (is (r/router?
          (ring/router
            ["/api" {:get {:handler identity
                           :roles #{:admin}}}]
            {:validate rrs/validate
             :data {:middleware [{:spec (s/keys :opt-un [::roles])
                                  :wrap (fn [handler]
                                          (fn [request]
                                            (handler request)))}]}})))
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid route data"
          (ring/router
            ["/api" {:get {:handler identity
                           :roles #{:adminz}}}]
            {:validate rrs/validate
             :data {:middleware [{:spec (s/keys :opt-un [::roles])
                                  :wrap (fn [handler]
                                          (fn [request]
                                              (handler request)))}]}}))))
  (testing "middleware cannot be a list"
    (is (thrown-with-msg?
         ExceptionInfo
         #"Invalid route data"
         (ring/router
          ["/api" {:handler    identity
                   :middleware '()}]
          {:validate rrs/validate})))))

(deftest coercion-spec-test
  (is (r/router?
        (ring/router
          ["/api"
           ["/plus/:e"
            {:get {:parameters {:query {:a string?}
                                :body {:b string?}
                                :form {:c string?}
                                :header {:d string?}
                                :path {:e string?}}
                   :responses {200 {:body {:total pos-int?}}
                               400 {:description "fail"}
                               500 {}}
                   :handler identity}}]]
          {:data {:middleware [rrc/coerce-exceptions-middleware
                               rrc/coerce-request-middleware
                               rrc/coerce-response-middleware]
                  :coercion reitit.coercion.spec/coercion}
           :validate rrs/validate})))

  (is (r/router?
        (ring/router
          ["/api"
           ["/plus/:e"
            {:get {:parameters {:query (s/keys)}
                   :handler identity}}]]
          {:data {:middleware [rrc/coerce-exceptions-middleware
                               rrc/coerce-request-middleware
                               rrc/coerce-response-middleware]
                  :coercion reitit.coercion.spec/coercion}
           :validate rrs/validate})))

  (is (thrown-with-msg?
        ExceptionInfo
        #"Invalid route data"
        (ring/router
          ["/api"
           ["/plus/:e"
            {:get {:responses {"200" {}}
                   :handler identity}}]]
          {:data {:middleware [rrc/coerce-exceptions-middleware
                               rrc/coerce-request-middleware
                               rrc/coerce-response-middleware]
                  :coercion reitit.coercion.spec/coercion}
           :validate rrs/validate}))))
