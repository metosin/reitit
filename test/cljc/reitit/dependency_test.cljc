(ns reitit.dependency-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.dependency :as rc])
  #?(:clj (:import [clojure.lang ExceptionInfo])))

(deftest post-order-test
  (let [base-middlewares [{:name ::bar, :provides #{:bar}, :requires #{:foo}, :wrap identity}
                          {:name ::baz, :provides #{:baz}, :requires #{:bar :foo}, :wrap identity}
                          {:name ::foo, :provides #{:foo}, :requires #{}, :wrap identity}]]
    (testing "happy cases"
      (testing "default ordering works"
        (is (= (rc/post-order base-middlewares)
               (into (vec (drop 2 base-middlewares)) (take 2 base-middlewares)))))

      (testing "custom provides and requires work"
        (is (= (rc/post-order (comp hash-set :name)
                              (fn [node] (into #{} (map (fn [k] (keyword "reitit.dependency-test" (name k))))
                                               (:requires node)))
                              base-middlewares)
               (into (vec (drop 2 base-middlewares)) (take 2 base-middlewares))))))

    (testing "errors"
      (testing "missing dependency detection"
        (is (thrown-with-msg? ExceptionInfo #"missing"
                              (rc/post-order (drop 1 base-middlewares)))))

      (testing "ambiguous dependency detection"
        (is (thrown-with-msg? ExceptionInfo #"multiple providers"
                              (rc/post-order (update-in base-middlewares [0 :provides] conj :foo)))))

      (testing "circular dependency detection"
        (is (thrown-with-msg? ExceptionInfo #"circular"
                              (rc/post-order (assoc-in base-middlewares [2 :requires] #{:baz}))))))))
