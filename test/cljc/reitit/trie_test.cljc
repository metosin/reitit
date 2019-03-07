(ns reitit.trie-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.trie :as trie]))

(deftest normalize-test
  (are [path expected]
    (is (= expected (trie/normalize path)))

    "/olipa/:kerran/avaruus", "/olipa/{kerran}/avaruus"
    "/olipa/{kerran}/avaruus", "/olipa/{kerran}/avaruus"
    "/olipa/{a.b/c}/avaruus", "/olipa/{a.b/c}/avaruus"
    "/olipa/kerran/*avaruus", "/olipa/kerran/{*avaruus}"
    "/olipa/kerran/{*avaruus}", "/olipa/kerran/{*avaruus}"
    "/olipa/kerran/{*valvavan.suuri/avaruus}", "/olipa/kerran/{*valvavan.suuri/avaruus}"))

(deftest tests
  (is (= (trie/->Match {} {:a 1})
         ((-> (trie/insert nil "/foo" {:a 1})
              (trie/compile)
              (trie/path-matcher)) "/foo")))

  (is (= (trie/->Match {} {:a 1})
         ((-> (trie/insert nil "/foo" {:a 1})
              (trie/insert "/foo/*bar" {:b 1})
              (trie/compile)
              (trie/path-matcher)) "/foo")))

  (is (= (trie/->Match {:bar "bar"} {:b 1})
         ((-> (trie/insert nil "/foo" {:a 1})
              (trie/insert "/foo/*bar" {:b 1})
              (trie/compile)
              (trie/path-matcher)) "/foo/bar")))

  (is (= (trie/->Match {} {:a 1})
         ((-> (trie/insert nil "" {:a 1})
              (trie/compile)
              (trie/path-matcher)) "")))

  #?(:clj
     (let [match ((-> (trie/insert nil "/:a" {:a 1} {::trie/parameters trie/record-parameters})
                      (trie/compile)
                      (trie/path-matcher)) "/a")]
       (is (record? (:params match)))
       (is (= (trie/->Match {:a "a"} {:a 1}) (update match :params (partial into {})))))))
