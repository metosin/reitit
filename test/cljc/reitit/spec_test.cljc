(ns reitit.spec-test
  (:require [clojure.test :refer [deftest testing is are]]
            [#?(:clj clojure.spec.test.alpha :cljs cljs.spec.test.alpha) :as stest]
            [clojure.spec.alpha :as s]
            [reitit.core :as reitit]
            [reitit.spec :as spec])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(stest/instrument `reitit/router `reitit/routes)

(deftest router-spec-test

  (testing "router"

    (testing "route-data"
      (are [data]
        (is (= true (reitit/router? (reitit/router data))))

        ["/api" {}]

        [["/api" {}]]

        ["/api"
         ["/ipa" ::ipa]
         ["/tea"
          ["/room"]]])

      (testing "with invalid routes"
        (are [data]
          (is (thrown-with-msg?
                ExceptionInfo
                #"Call to #'reitit.core/router did not conform to spec"
                (reitit/router
                  data)))

          ;; missing slash
          ["invalid" {}]

          ;; path
          [:invalid {}]

          ;; vector meta
          ["/api" []
           ["/ipa"]])))

    (testing "routes conform to spec (can't spec protocol functions)"
      (is (= true (s/valid? ::spec/routes (reitit/routes (reitit/router ["/ping"]))))))

    (testing "options"

      (are [opts]
        (is (= true (reitit/router? (reitit/router ["/api"] opts))))

        {:path "/"}
        {:meta {}}
        {:expand (fn [_ _] {})}
        {:coerce (fn [route _] route)}
        {:compile (fn [_ _])}
        {:conflicts (fn [_])}
        {:router reitit/linear-router})

      (are [opts]
        (is (thrown-with-msg?
              ExceptionInfo
              #"Call to #'reitit.core/router did not conform to spec"
              (reitit/router
                ["/api"] opts)))

        {:path ""}
        {:path nil}
        {:meta nil}
        {:expand nil}
        {:coerce nil}
        {:compile nil}
        {:conflicts nil}
        {:router nil}))))
