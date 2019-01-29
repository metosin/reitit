(ns reitit.trie-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.trie :as rt]))

(deftest tests
  (is (= (rt/->Match {:a 1} {})
         (-> (rt/insert nil "/foo" {:a 1})
             (rt/compile)
             (rt/lookup "/foo"))))

  (is (= (rt/->Match {:a 1} {})
         (-> (rt/insert nil "/foo" {:a 1})
             (rt/insert "/foo/*bar" {:b 1})
             (rt/compile)
             (rt/lookup "/foo"))))

  (is (= (rt/->Match {:b 1} {:bar "bar"})
         (-> (rt/insert nil "/foo" {:a 1})
             (rt/insert "/foo/*bar" {:b 1})
             (rt/compile)
             (rt/lookup "/foo/bar"))))

  (is (= (rt/->Match {:a 1} {})
         (-> (rt/insert nil "" {:a 1})
             (rt/compile)
             (rt/lookup "")))))
