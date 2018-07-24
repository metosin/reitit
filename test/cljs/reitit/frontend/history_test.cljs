(ns reitit.frontend.history-test
  (:require [clojure.test :refer [deftest testing is are async]]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.test-utils :refer [capture-console]]
            [goog.events :as gevents]))

(def browser (exists? js/window))

(def router (r/router ["/"
                       ["" ::frontpage]
                       ["foo" ::foo]
                       ["bar/:id" ::bar]]))

(deftest fragment-history-test
  (when browser
    (gevents/removeAll js/window goog.events.EventType.POPSTATE)
    (gevents/removeAll js/window goog.events.EventType.HASHCHANGE)

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
                 messages))))

      (rfh/stop! history))))

(deftest fragment-history-routing-test
  (when browser
    (gevents/removeAll js/window goog.events.EventType.POPSTATE)
    (gevents/removeAll js/window goog.events.EventType.HASHCHANGE)

    (async done
      (let [n (atom 0)
            history (rfh/start! router
                                (fn [match history]
                                  (let [url (rfh/-get-path history)]
                                    (case (swap! n inc)
                                      1 (do (is (= "/" url)
                                                "start at root")
                                            (rfh/push-state history ::foo))
                                      2 (do (is (= "/foo" url)
                                                "push-state")
                                            (.back js/window.history))
                                      3 (do (is (= "/" url)
                                                "go back")
                                            (rfh/push-state history ::bar {:id 1}))
                                      4 (do (is (= "/bar/1" url)
                                                "push-state 2")
                                            (rfh/replace-state history ::bar {:id 2}))
                                      5 (do (is (= "/bar/2" url)
                                                "replace-state")
                                            (.back js/window.history))
                                      6 (do (is (= "/" url)
                                                "go back after replace state")
                                            (rfh/stop! history)
                                            (done))
                                      (do (is false "extra event")))))
                                {:use-fragment true})]))))

(deftest html5-history-test
  (when browser
    (gevents/removeAll js/window goog.events.EventType.POPSTATE)
    (gevents/removeAll js/window goog.events.EventType.HASHCHANGE)

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
                 messages))))

      (rfh/stop! history))))

(deftest html5-history-routing-test
  (when browser
    (gevents/removeAll js/window goog.events.EventType.POPSTATE)
    (gevents/removeAll js/window goog.events.EventType.HASHCHANGE)

    (async done
      (let [n (atom 0)
            history (rfh/start! router
                                (fn [match history]
                                  (let [url (rfh/-get-path history)]
                                    (case (swap! n inc)
                                      1 (do (rfh/push-state history ::frontpage))
                                      2 (do (is (= "/" url)
                                                "start at root")
                                            (rfh/push-state history ::foo))
                                      3 (do (is (= "/foo" url)
                                                "push-state")
                                            (.back js/window.history))
                                      4 (do (is (= "/" url)
                                                "go back")
                                            (rfh/push-state history ::bar {:id 1}))
                                      5 (do (is (= "/bar/1" url)
                                                "push-state 2")
                                            (rfh/replace-state history ::bar {:id 2}))
                                      6 (do (is (= "/bar/2" url)
                                                "replace-state")
                                            (.back js/window.history))
                                      7 (do (is (= "/" url)
                                                "go back after replace state")
                                            (rfh/stop! history)
                                            (done))
                                      (do (is false "extra event")))))
                                {:use-fragment false})]))))
