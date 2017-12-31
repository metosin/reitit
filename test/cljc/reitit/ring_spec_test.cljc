(ns reitit.ring-spec-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.ring.spec :as rrs]
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
            {:validate rrs/validate-spec!})))
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid route data"
          (ring/router
            ["/api" {:handler identity
                     :name "kikka"}]
            {:validate rrs/validate-spec!}))))

  (testing "all endpoints are validated"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid route data"
          (ring/router
            ["/api" {:patch {:handler "identity"}}]
            {:validate rrs/validate-spec!}))))

  (testing "spec can be overridden"
    (is (r/router?
          (ring/router
            ["/api" {:handler "identity"}]
            {:spec (s/spec any?)
             :validate rrs/validate-spec!})))

    (testing "predicates are not allowed"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Not all specs satisfy the Spec protocol"
            (ring/router
              ["/api" {:handler "identity"}]
              {:spec any?
               :validate rrs/validate-spec!})))))

  (testing "middleware can contribute to specs"
    (is (r/router?
          (ring/router
            ["/api" {:get {:handler identity
                           :roles #{:admin}}}]
            {:validate rrs/validate-spec!
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
            {:validate rrs/validate-spec!
             :data {:middleware [{:spec (s/keys :opt-un [::roles])
                                  :wrap (fn [handler]
                                          (fn [request]
                                            (handler request)))}]}})))))
