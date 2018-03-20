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

(deftest into-string-test
  (is (= "1" (impl/into-string 1)))
  (is (= "2.2" (impl/into-string 2.2)))
  (is (= "kikka" (impl/into-string "kikka")))
  (is (= "kikka" (impl/into-string :kikka)))
  (is (= "reitit.impl-test/kikka" (impl/into-string ::kikka))))

(deftest url-encode-and-decode-test
  (is (= "reitit.impl-test%2Fkikka" (-> ::kikka
                                        impl/into-string
                                        impl/url-encode)))
  (is (= "reitit.impl-test/kikka" (-> ::kikka
                                       impl/into-string
                                       impl/url-encode
                                       impl/url-decode))))

(deftest path-params-test
  (is (= {:n "1"
          :d "2.2"
          :s "kikka"
          :k "kikka"
          :qk "reitit.impl-test%2Fkikka"}
         (impl/path-params {:n 1
                            :d 2.2
                            :s "kikka"
                            :k :kikka
                            :qk ::kikka}))))
