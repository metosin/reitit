(ns reitit.impl-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.impl :as impl]))

(deftest segments-test
  (is (= ["" "api" "ipa" "beer" "craft" "bisse"]
         (into [] (impl/segments "/api/ipa/beer/craft/bisse"))))
  (is (= ["" "a" "" "b" "" "c" ""]
         (into [] (impl/segments "/a//b//c/")))))
