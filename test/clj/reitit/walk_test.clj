(ns reitit.walk-test
  (:require
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as prop]
   [clojure.test.check.generators :as gen]
   [clojure.walk :as walk]
   [reitit.walk :as sut]))

(defspec keywordize=walk-keywordize
  10000
  (prop/for-all [v gen/any-equatable]
                (= (sut/keywordize-keys v)
                   (walk/keywordize-keys v))))
