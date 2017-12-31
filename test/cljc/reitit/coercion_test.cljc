(ns reitit.coercion-test
  (:require [clojure.test :refer [deftest testing is]]
            [schema.core :as s]
            [reitit.core :as r]
            [reitit.coercion :as coercion]
            [reitit.coercion.spec :as spec]
            [reitit.coercion.schema :as schema])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(deftest spec-coercion-test
  (let [r (r/router
            [["/schema" {:coercion schema/coercion}
              ["/:number/:keyword" {:name ::user
                                    :parameters {:path {:number s/Int
                                                        :keyword s/Keyword}}}]]
             ["/spec" {:coercion spec/coercion}
              ["/:number/:keyword" {:name ::user
                                    :parameters {:path {:number int?
                                                        :keyword keyword?}}}]]
             ["/none"
              ["/:number/:keyword" {:name ::user
                                    :parameters {:path {:number int?
                                                        :keyword keyword?}}}]]]
            {:compile coercion/compile-request-coercers})]

    (testing "schema-coercion"
      (testing "succeeds"
        (let [m (r/match-by-path r "/schema/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}}
                 (coercion/coerce! m)))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/schema/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    (testing "spec-coercion"
      (testing "succeeds"
        (let [m (r/match-by-path r "/spec/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}}
                 (coercion/coerce! m)))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/spec/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    (testing "no coercion defined"
      (testing "doesn't coerce"
        (let [m (r/match-by-path r "/none/1/abba")]
          (is (= nil (coercion/coerce! m))))
        (let [m (r/match-by-path r "/none/kikka/abba")]
          (is (= nil (coercion/coerce! m))))))))
