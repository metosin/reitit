(ns reitit.trie-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.trie :as trie]))

(deftest into-set-test
  (is (= #{} (trie/into-set nil)))
  (is (= #{} (trie/into-set [])))
  (is (= #{1} (trie/into-set 1)))
  (is (= #{1 2} (trie/into-set [1 2 1]))))

(deftest conflicting-paths?-test
  (are [c? p1 p2]
    (is (= c? (trie/conflicting-paths? p1 p2 nil)))

    true "/a" "/a"
    true "/a" "/:a"
    true "/a/:b" "/:a/b"
    true "/ab/:b" "/:a/ba"
    true "/*a" "/:a/ba/ca"

    true "/a" "/{a}"
    true "/a/{b}" "/{a}/b"
    true "/ab/{b}" "/{a}/ba"
    true "/{*a}" "/{a}/ba/ca"

    false "/a" "/:a/b"
    false "/a" "/:a/b"

    ;; #320
    false "/repo/:owner/:repo/contributors" "/repo/:owner/:repo/languages"
    false "/repo/:owner/:repo/contributors" "/repo/:not-owner/:not-repo/language"

    false "/a" "/{a}/b"
    false "/a" "/{a}/b"))

(deftest split-path-test
  (testing "colon"
    (doseq [syntax [:colon #{:colon}]]
      (are [path expected]
        (is (= expected (trie/split-path path {:syntax syntax})))

        "/olipa/:kerran/avaruus", ["/olipa/" (trie/->Wild :kerran) "/avaruus"]
        "/olipa/{kerran}/avaruus", ["/olipa/{kerran}/avaruus"]
        "/olipa/{a.b/c}/avaruus", ["/olipa/{a.b/c}/avaruus"]
        "/olipa/kerran/*avaruus", ["/olipa/kerran/" (trie/->CatchAll :avaruus)]
        "/olipa/kerran/{*avaruus}", ["/olipa/kerran/{" (trie/->CatchAll (keyword "avaruus}"))]
        "/olipa/kerran/{*valtavan.suuri/avaruus}", ["/olipa/kerran/{" (trie/->CatchAll (keyword "valtavan.suuri/avaruus}"))])))

  (testing "bracket"
    (doseq [syntax [:bracket #{:bracket}]]
      (are [path expected]
        (is (= expected (trie/split-path path {:syntax syntax})))

        "/olipa/:kerran/avaruus", ["/olipa/:kerran/avaruus"]
        "/olipa/{kerran}/avaruus", ["/olipa/" (trie/->Wild :kerran) "/avaruus"]
        "/olipa/{a.b/c}/avaruus", ["/olipa/" (trie/->Wild :a.b/c) "/avaruus"]
        "/olipa/kerran/*avaruus", ["/olipa/kerran/*avaruus"]
        "/olipa/kerran/{*avaruus}", ["/olipa/kerran/" (trie/->CatchAll :avaruus)]
        "/olipa/kerran/{*valtavan.suuri/avaruus}", ["/olipa/kerran/" (trie/->CatchAll :valtavan.suuri/avaruus)])))

  (testing "both"
    (doseq [syntax [#{:bracket :colon}]]
      (are [path expected]
        (is (= expected (trie/split-path path {:syntax syntax})))

        "/olipa/:kerran/avaruus", ["/olipa/" (trie/->Wild :kerran) "/avaruus"]
        "/olipa/{kerran}/avaruus", ["/olipa/" (trie/->Wild :kerran) "/avaruus"]
        "/olipa/{a.b/c}/avaruus", ["/olipa/" (trie/->Wild :a.b/c) "/avaruus"]
        "/olipa/kerran/*avaruus", ["/olipa/kerran/" (trie/->CatchAll :avaruus)]
        "/olipa/kerran/{*avaruus}", ["/olipa/kerran/" (trie/->CatchAll :avaruus)]
        "/olipa/kerran/{*valtavan.suuri/avaruus}", ["/olipa/kerran/" (trie/->CatchAll :valtavan.suuri/avaruus)])))

  (testing "nil"
    (doseq [syntax [nil]]
      (are [path expected]
        (is (= expected (trie/split-path path {:syntax syntax})))

        "/olipa/:kerran/avaruus", ["/olipa/:kerran/avaruus"]
        "/olipa/{kerran}/avaruus", ["/olipa/{kerran}/avaruus"]
        "/olipa/{a.b/c}/avaruus", ["/olipa/{a.b/c}/avaruus"]
        "/olipa/kerran/*avaruus", ["/olipa/kerran/*avaruus"]
        "/olipa/kerran/{*avaruus}", ["/olipa/kerran/{*avaruus}"]
        "/olipa/kerran/{*valtavan.suuri/avaruus}", ["/olipa/kerran/{*valtavan.suuri/avaruus}"]))))

(deftest normalize-test
  (are [path expected]
    (is (= expected (trie/normalize path nil)))

    "/olipa/:kerran/avaruus", "/olipa/{kerran}/avaruus"
    "/olipa/{kerran}/avaruus", "/olipa/{kerran}/avaruus"
    "/olipa/{a.b/c}/avaruus", "/olipa/{a.b/c}/avaruus"
    "/olipa/kerran/*avaruus", "/olipa/kerran/{*avaruus}"
    "/olipa/kerran/{*avaruus}", "/olipa/kerran/{*avaruus}"
    "/olipa/kerran/{*valtavan.suuri/avaruus}", "/olipa/kerran/{*valtavan.suuri/avaruus}"))

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
