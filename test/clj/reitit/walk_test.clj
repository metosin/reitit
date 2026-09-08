(ns reitit.walk-test
  (:require
   [clojure.test :refer [deftest is]]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as prop]
   [clojure.test.check.generators :as gen]
   [clojure.walk :as walk]
   [reitit.walk :as sut]))

(deftest keywordize-subvec
  (is (= (sut/keywordize-keys (subvec [{"a" 1} {"b" 2}] 0 2))
         (walk/keywordize-keys (subvec [{"a" 1} {"b" 2}] 0 2)))))

(defspec keywordize=walk-keywordize
  10000
  (prop/for-all [v gen/any-equatable]
                (= (sut/keywordize-keys v)
                   (walk/keywordize-keys v))))
