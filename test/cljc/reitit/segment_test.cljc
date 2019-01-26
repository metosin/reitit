(ns reitit.segment-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.segment :as s]))

(deftest tests
  (is (= (s/->Match {:a 1} {})
         (-> (s/insert nil "/foo" {:a 1})
             (s/compile)
             (s/lookup "/foo"))))

  (is (= (s/->Match {:a 1} {})
         (-> (s/insert nil "/foo" {:a 1})
             (s/insert "/foo/*bar" {:b 1})
             (s/compile)
             (s/lookup "/foo"))))

  (is (= (s/->Match {:b 1} {:bar "bar"})
         (-> (s/insert nil "/foo" {:a 1})
             (s/insert "/foo/*bar" {:b 1})
             (s/compile)
             (s/lookup "/foo/bar"))))

  (is (= (s/->Match {:a 1} {})
         (-> (s/insert nil "" {:a 1})
             (s/compile)
             (s/lookup "")))))
