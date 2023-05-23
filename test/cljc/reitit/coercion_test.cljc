(ns reitit.coercion-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.experimental.lite :as l]
            [reitit.coercion :as coercion]
            [reitit.coercion.malli]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [reitit.core :as r]
            [schema.core :as s]
            [clojure.spec.alpha :as cs]
            [spec-tools.data-spec :as ds])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(cs/def ::number int?)
(cs/def ::keyword keyword?)
(cs/def ::int int?)
(cs/def ::ints (cs/coll-of int? :kind vector))
(cs/def ::map (cs/map-of int? int?))

(deftest coercion-test
  (let [r (r/router
           [["/schema" {:coercion reitit.coercion.schema/coercion}
             ["/:number" {:parameters {:path {:number s/Int}}}
              ["/:keyword" {:parameters {:path {:keyword s/Keyword}
                                         :query (s/maybe {:int s/Int, :ints [s/Int], :map {s/Int s/Int}})}}]]]

            ["/malli" {:coercion reitit.coercion.malli/coercion}
             ["/:number" {:parameters {:path [:map [:number int?]]}}
              ["/:keyword" {:parameters {:path [:map [:keyword keyword?]]
                                         :query [:maybe [:map [:int int?]
                                                         [:ints [:vector int?]]
                                                         [:map [:map-of int? int?]]]]}}]]]

            ["/malli-lite" {:coercion reitit.coercion.malli/coercion}
             ["/:number" {:parameters {:path {:number int?}}}
              ["/:keyword" {:parameters {:path {:keyword keyword?}
                                         :query (l/maybe {:int int?
                                                          :ints (l/vector int?)
                                                          :map (l/map-of int? int?)})}}]]]

            #_["/spec" {:coercion reitit.coercion.spec/coercion}
               ["/:number" {:parameters {:path (cs/keys :req-un [::number])}}
                ["/:keyword" {:parameters {:path (cs/keys :req-un [::keyword])
                                           :query (cs/nilable (cs/keys :req-un [::int ::ints ::map]))}}]]]

            ["/spec-shallow" {:coercion reitit.coercion.spec/coercion}
             ["/:number/:keyword" {:parameters {:path (cs/keys :req-un [::number ::keyword])
                                                :query (cs/nilable (cs/keys :req-un [::int ::ints ::map]))}}]]

            ["/data-spec" {:coercion reitit.coercion.spec/coercion}
             ["/:number" {:parameters {:path {:number int?}}}
              ["/:keyword" {:parameters {:path {:keyword keyword?}
                                         :query (ds/maybe {:int int?, :ints [int?], :map {int? int?}})}}]]]

            ["/none"
             ["/:number" {:parameters {:path {:number int?}}}
              ["/:keyword" {:parameters {:path {:keyword keyword?}}}]]]]
           {:compile coercion/compile-request-coercers})]

    (testing "schema-coercion"
      (testing "succeeds"
        (let [m (r/match-by-path r "/schema/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query nil}
                 (coercion/coerce! m))))
        (let [m (r/match-by-path r "/schema/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query {:int 10, :ints [1, 2, 3], :map {1 1, 2 2}}}
                 (coercion/coerce! (assoc m :query-params {"int" "10", "ints" ["1" "2" "3"], "map" {:1 "1", "2" "2"}}))))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/schema/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    (testing "malli-coercion"
      (testing "succeeds"
        (let [m (r/match-by-path r "/malli/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query nil}
                 (coercion/coerce! m))))
        (let [m (r/match-by-path r "/malli/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query {:int 10, :ints [1, 2, 3], :map {1 1, 2 2}}}
                 (coercion/coerce! (assoc m :query-params {"int" "10", "ints" ["1" "2" "3"], "map" {:1 "1", "2" "2"}}))))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/malli/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    (testing "malli-lite coercion"
      (testing "succeeds"
        (let [m (r/match-by-path r "/malli-lite/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query nil}
                 (coercion/coerce! m))))
        (let [m (r/match-by-path r "/malli-lite/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query {:int 10, :ints [1, 2, 3], :map {1 1, 2 2}}}
                 (coercion/coerce! (assoc m :query-params {"int" "10", "ints" ["1" "2" "3"], "map" {:1 "1", "2" "2"}}))))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/malli-lite/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    #_(testing "spec-coercion"
      (testing "fails"
        (let [m (r/match-by-path r "/spec/1/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))
        (let [m (r/match-by-path r "/spec/1/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m)))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/spec/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    (testing "spec-coercion (shallow)"
      (testing "succeeds"
        (let [m (r/match-by-path r "/spec-shallow/1/abba")]
          (def MATCH m)
          (is (= {:path {:keyword :abba, :number 1}, :query nil}
                 (coercion/coerce! m))))
        (let [m (r/match-by-path r "/spec-shallow/1/abba")]
          (is (= {:path {:keyword :abba, :number 1}, :query {:int 10, :ints [1, 2, 3], :map {1 1, #_#_2 2}}}
                 (coercion/coerce! (assoc m :query-params {"int" "10", "ints" ["1" "2" "3"], "map" {:1 "1"}, #_#_"2" "2"}))))))
      (testing "throws with invalid input"
        (let [m (r/match-by-path r "/spec-shallow/kikka/abba")]
          (is (thrown? ExceptionInfo (coercion/coerce! m))))))

    ;; TODO: :map-of fails with string-keys
    #_(testing "data-spec-coercion"
        (testing "succeeds"
          (let [m (r/match-by-path r "/data-spec/1/abba")]
            (is (= {:path {:keyword :abba, :number 1}, :query nil}
                   (coercion/coerce! m))))
          (let [m (r/match-by-path r "/data-spec/1/abba")]
            (is (= {:path {:keyword :abba, :number 1}, :query {:int 10, :ints [1, 2, 3], :map {1 1, #_#_2 2}}}
                   (coercion/coerce! (assoc m :query-params {"int" "10", "ints" ["1" "2" "3"], "map" {:1 "1"}, #_#_"2" "2"}))))))
        (testing "throws with invalid input"
          (let [m (r/match-by-path r "/data-spec/kikka/abba")]
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
