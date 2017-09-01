(ns reitit.spec-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.alpha :as s]
            [reitit.core :as reitit]
            [reitit.spec :as spec])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(stest/instrument `reitit/router)

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

    (testing "options"

        (are [opts]
          (is (= true (reitit/router? (reitit/router ["/api"] opts))))

          {:path "/"}

          {:meta {}}

          #_{:coerce (fn [_ _] ["/"])}
          )


        (are [opts]
          (is (thrown-with-msg?
                ExceptionInfo
                #"Call to #'reitit.core/router did not conform to spec"
                (reitit/router
                  ["/api"] opts)))

          {:meta 1}))))
