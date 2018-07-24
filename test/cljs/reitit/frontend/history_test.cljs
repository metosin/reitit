(ns reitit.frontend.history-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.test-utils :refer [capture-console]]))

(def browser (exists? js/window))

(def router (r/router ["/"
                       ["" ::frontpage]
                       ["foo" ::foo]
                       ["bar/:id" ::bar]]))

(deftest fragment-history-test
  (when browser
    (let [history (rfh/start! router (fn [_]) {:use-fragment true})]

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
    (let [history (rfh/start! router (fn [_]) {:use-fragment false})]

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
