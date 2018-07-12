(ns reitit.frontend.history-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.test-utils :refer [capture-console]]))

(def browser (exists? js/window))

(deftest fragment-history-test
  (when browser
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
               (rfh/href history ::bar {:id 5} {:q "x"})))
        (let [{:keys [value messages]} (capture-console
                                         (fn []
                                           (rfh/href history ::asd)))]
          (is (= nil value))
          (is (= [{:type :warn
                   :message ["missing route" ::asd]}]
                 messages)))))))

(deftest html5-history-test
  (when browser
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
               (rfh/href history ::bar {:id 5} {:q "x"})))
        (let [{:keys [value messages]} (capture-console
                                         (fn []
                                           (rfh/href history ::asd)))]
          (is (= nil value))
          (is (= [{:type :warn
                   :message ["missing route" ::asd]}]
                 messages)))))))
