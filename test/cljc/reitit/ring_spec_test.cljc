(ns reitit.ring-spec-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.ring.spec :as rrs]
            [reitit.core :as r]
            [reitit.spec :as rs])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))


(deftest route-data-validation-test
  (testing "validation is turned off by default"
    (is (true? (r/router?
                 (r/router
                   ["/api" {:handler "identity"}])))))

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
            {:validate rrs/validate-spec!})))
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid route data"
          (ring/router
            ["/api" {:handler identity
                     :middleware [{}]}]
            {:validate rrs/validate-spec!}))))

  (testing "all endpoints are validated"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid route data"
          (ring/router
            ["/api" {:patch {:handler "identity"}}]
            {:validate rrs/validate-spec!}))))

  (testing "spec can be overridden"
    (is (true? (r/router?
                 (ring/router
                   ["/api" {:handler "identity"}]
                   {:spec any?
                    :validate rrs/validate-spec!}))))))
