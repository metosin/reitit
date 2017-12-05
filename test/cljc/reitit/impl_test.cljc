(ns reitit.impl-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.impl :as impl]))

(deftest segments-test
  (is (= ["" "api" "ipa" "beer" "craft" "bisse"]
         (into [] (impl/segments "/api/ipa/beer/craft/bisse"))))
  (is (= ["" "a" "" "b" "" "c" ""]
         (into [] (impl/segments "/a//b//c/")))))

(deftest strip-nils-test
  (is (= {:a 1, :c false} (impl/strip-nils {:a 1, :b nil, :c false}))))
