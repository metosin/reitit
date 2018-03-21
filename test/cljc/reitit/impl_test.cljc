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
          :n1 "-1"
          :n2 "1"
          :n3 "1"
          :n4 "1"
          :n5 "1"
          :d "2.2"
          :b "true"
          :s "kikka"
          :u "c2541900-17a7-4353-9024-db8ac258ba4e"
          :k "kikka"
          :qk "reitit.impl-test%2Fkikka"}
         (impl/path-params {:n 1
                            :n1 -1
                            :n2 (long 1)
                            :n3 (int 1)
                            :n4 (short 1)
                            :n5 (byte 1)
                            :d 2.2
                            :b true
                            :s "kikka"
                            :u #uuid "c2541900-17a7-4353-9024-db8ac258ba4e"
                            :k :kikka
                            :qk ::kikka}))))
