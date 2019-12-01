(ns reitit.coercion-test
  (:require [clojure.test :refer [deftest testing is]]
            [schema.core :as s]
            [spec-tools.data-spec :as ds]
            [reitit.core :as r]
            [reitit.coercion :as coercion]
            [reitit.coercion.spec]
            [reitit.coercion.malli]
            [reitit.coercion.schema])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(deftest coercion-test
  (let [r (r/router
            [["/schema" {:coercion reitit.coercion.schema/coercion}
              ["/:number/:keyword" {:parameters {:path {:number s/Int
                                                        :keyword s/Keyword}
                                                 :query (s/maybe {:int s/Int, :ints [s/Int], :map {s/Int s/Int}})}}]]
             ["/malli" {:coercion reitit.coercion.malli/coercion}
              ["/:number/:keyword" {:parameters {:path [:map [:number int?] [:keyword keyword?]]
                                                 :query [:maybe [:map [:int int?]
                                                                 [:ints [:vector int?]]
                                                                 [:map [:map-of int? int?]]]]}}]]
             ["/spec" {:coercion reitit.coercion.spec/coercion}
              ["/:number/:keyword" {:parameters {:path {:number int?
                                                        :keyword keyword?}
                                                 :query (ds/maybe {:int int?, :ints [int?], :map {int? int?}})}}]]
             ["/none"
              ["/:number/:keyword" {:parameters {:path {:number int?
                                                        :keyword keyword?}}}]]]
            {:compile coercion/compile-request-coercers})]

    (testing "schema-coercion"
      (testing "succeeds"
        (let [m (r/match-by-path r "/schema/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query nil}
                 (coercion/coerce! m))))
        (let [m (r/match-by-path r "/schema/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query {:int 10, :ints [1,2,3], :map {1 1}}}
                 (coercion/coerce! (assoc m :query-params {"int" "10", "ints" ["1" "2" "3"], "map" {:1 "1"}}))))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/schema/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    (testing "malli-coercion"
      (testing "succeeds"
        (let [m (r/match-by-path r "/malli/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query nil}
                 (coercion/coerce! m))))
        (let [m (r/match-by-path r "/malli/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query {:int 10, :ints [1,2,3], :map {1 1}}}
                 (coercion/coerce! (assoc m :query-params {"int" "10", "ints" ["1" "2" "3"], "map" {:1 "1"}}))))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/malli/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    (testing "spec-coercion"
      (testing "succeeds"
        (let [m (r/match-by-path r "/spec/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query nil}
                 (coercion/coerce! m))))
        (let [m (r/match-by-path r "/schema/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query {:int 10, :ints [1,2,3], :map {1 1}}}
                 (coercion/coerce! (assoc m :query-params {"int" "10", "ints" ["1" "2" "3"], "map" {:1 "1"}}))))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/spec/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    (testing "no coercion defined"
      (testing "doesn't coerce"
        (let [m (r/match-by-path r "/none/1/abba")]
          (is (= nil (coercion/coerce! m))))
        (let [m (r/match-by-path r "/none/kikka/abba")]
          (is (= nil (coercion/coerce! m))))))))

(defn match-by-path-and-coerce! [router path]
  (if-let [match (r/match-by-path router path)]
    (assoc match :parameters (coercion/coerce! match))))

(deftest data-spec-example-test
  (let [router (r/router
                 ["/:company/users/:user-id" {:name ::user-view
                                              :coercion reitit.coercion.spec/coercion
                                              :parameters {:path {:company string?
                                                                  :user-id int?}}}]
                 {:compile coercion/compile-request-coercers})]
    (is (= {:path {:user-id 123, :company "metosin"}}
           (:parameters (match-by-path-and-coerce! router "/metosin/users/123"))))))
