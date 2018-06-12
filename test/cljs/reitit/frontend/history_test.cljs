(ns reitit.frontend.history-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r]
            [reitit.frontend.history :as rfh]))

(deftest fragment-history-test
  (let [router (r/router ["/"
                            ["" ::frontpage]
                            ["foo" ::foo]
                            ["bar/:id" ::bar]])
        history (rfh/start! router
                            (fn [_])
                            {:use-fragment true
                             :path-prefix "/"})]

    (testing "creating urls"
      (is (= "#/foo"
             (rfh/href history ::foo)))
      (is (= "#/bar/5"
             (rfh/href history ::bar {:id 5})))
      (is (= "#/bar/5?q=x"
             (rfh/href history ::bar {:id 5} {:q "x"}))))))

(deftest html5-history-test
  (let [router (r/router ["/"
                            ["" ::frontpage]
                            ["foo" ::foo]
                            ["bar/:id" ::bar]])
        history (rfh/start! router
                            (fn [_])
                            {:use-fragment false
                             :path-prefix "/"})]

    (testing "creating urls"
      (is (= "/foo"
             (rfh/href history ::foo)))
      (is (= "/bar/5"
             (rfh/href history ::bar {:id 5})))
      (is (= "/bar/5?q=x"
             (rfh/href history ::bar {:id 5} {:q "x"}))))))
