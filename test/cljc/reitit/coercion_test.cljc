(ns reitit.coercion-test
  (:require [clojure.spec.alpha :as cs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [malli.experimental.lite :as l]
            [reitit.coercion :as coercion]
            [reitit.coercion.malli]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [reitit.core :as r]
            [schema.core :as s]
            [spec-tools.data-spec :as ds]
            [malli.transform :as mt])
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

(deftest malli-query-parameter-coercion-test
  (let [router (fn [coercion]
                 (r/router ["/test"
                            {:coercion coercion
                             :parameters {:query [:map
                                                  [:a [:string {:default "a"}]]
                                                  [:x {:optional true} [:keyword {:default :a}]]]}}]
                           {:compile coercion/compile-request-coercers}))]
    (testing "default values for :optional query keys do not get added"
      (is (= {:query {:a "a"}}
             (-> (r/match-by-path (router reitit.coercion.malli/coercion) "/test")
                 (assoc :query-params {})
                 (coercion/coerce!)))))
    (testing "default values for :optional query keys get added when :malli.transform/add-optional-keys is set"
      (is (= {:query {:a "a" :x :a}}
             (-> (r/match-by-path (router (reitit.coercion.malli/create
                                           (assoc reitit.coercion.malli/default-options
                                                  :default-values {:malli.transform/add-optional-keys true}))) "/test")
                 (assoc :query-params {})
                 (coercion/coerce!)))))
    (testing "default values can be disabled"
      (is (thrown-with-msg?
           ExceptionInfo
           #"Request coercion failed"
           (-> (r/match-by-path (router (reitit.coercion.malli/create
                                         (assoc reitit.coercion.malli/default-options
                                                :default-values false))) "/test")
               (assoc :query-params {})
               (coercion/coerce!)))))))

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

(deftest match->path-parameter-coercion-test
  (testing "default handling for query-string collection"
    (let [router (r/router ["/:a/:b" ::route])]
      (is (= "/olipa/kerran?x=a&x=b"
             (-> router
                 (r/match-by-name! ::route {:a "olipa", :b "kerran"})
                 (coercion/match->path {:x [:a :b]}))))

      (is (= "/olipa/kerran?x=a&x=b&extra=extra-param"
             (-> router
                 (r/match-by-name! ::route {:a "olipa", :b "kerran"})
                 (coercion/match->path {:x [:a :b]
                                        :extra "extra-param"}))))))

  (testing "custom encode/string for a collection"
    (let [router (r/router ["/:a/:b"
                            {:name ::route
                             :coercion reitit.coercion.malli/coercion
                             :parameters {:query [:map
                                                  [:x
                                                   [:vector
                                                    {:encode/string (fn [xs]
                                                                      (str/join "," (map name xs)))
                                                     :decode/string (fn [s]
                                                                      (mapv keyword (str/split s #",")))}
                                                    :keyword]]]}}]
                           {:compile coercion/compile-request-coercers})
          match (r/match-by-name! router ::route {:a "olipa", :b "kerran"})]
      (is (= {:x "a,b"}
             (coercion/coerce-query-params match {:x [:a :b]})))

      ;; NOTE: "," is urlencoded by the impl/query-string step
      (is (= "/olipa/kerran?x=a%2Cb"
             (coercion/match->path match {:x [:a :b]})))

      (testing "extra query-string parameters aren't removed by coercion"
        (is (= "/olipa/kerran?x=a%2Cb&extra=extra-param"
               (-> router
                   (r/match-by-name! ::route {:a "olipa", :b "kerran"})
                   (coercion/match->path {:x [:a :b]
                                   :extra "extra-param"})))))

      (is (= {:query {:x [:a :b]}}
             (-> (r/match-by-path router "/olipa/kerran")
                 (assoc :query-params {:x "a,b"})
                 (coercion/coerce!))))))

  (testing "encoding and multiple query param values"
    (let [router (r/router ["/:a/:b"
                            {:name ::route
                             :coercion reitit.coercion.malli/coercion
                             :parameters {:query [:map
                                                  [:x
                                                   [:vector
                                                    [:keyword
                                                     ;; For query strings encode only calls encode, so no need to check if decode if value is encoded or not.
                                                     {:decode/string (fn [s] (keyword (subs s 2)))
                                                      :encode/string (fn [k] (str "__" (name k)))}]]]]}}]
                           {:compile coercion/compile-request-coercers})]
      (is (= "/olipa/kerran?x=__a&x=__b"
             (-> router
                 (r/match-by-name! ::route {:a "olipa", :b "kerran"})
                 (coercion/match->path {:x [:a :b]}))))

      (is (= {:query {:x [:a :b]}}
             (-> (r/match-by-path router "/olipa/kerran")
                 (assoc :query-params {:x ["__a" "__b"]})
                 (coercion/coerce!)))))))
