(ns reitit.frontend.easy-test
  (:require [clojure.test :refer [deftest testing is are async]]
            [reitit.core :as r]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.history :as rfh]
            [goog.events :as gevents]))

(def browser (exists? js/window))

(def router (r/router ["/"
                       ["" ::frontpage]
                       ["foo" ::foo]
                       ["bar/:id" ::bar]]))

;; TODO: Only tests fragment history, also test HTML5?

(deftest easy-history-routing-test
  (when browser
    (gevents/removeAll js/window goog.events.EventType.POPSTATE)
    (gevents/removeAll js/window goog.events.EventType.HASHCHANGE)

    (async done
      (let [n (atom 0)]
        ;; This also validates that rfe/history is set during initial on-navigate call
        (rfe/start! router
                    (fn on-navigate [match history]
                      (let [url (rfh/-get-path history)]
                        (case (swap! n inc)
                          1 (do (is (some? (:popstate-listener history)))
                                (is (= "/" url)
                                    "start at root")
                                (rfe/push-state ::foo))
                          2 (do (is (= "/foo" url)
                                    "push-state")
                                (.back js/window.history))
                          3 (do (is (= "/" url)
                                    "go back")
                                (rfe/push-state ::bar {:id 1}))
                          4 (do (is (= "/bar/1" url)
                                    "push-state 2")
                                (rfe/replace-state ::bar {:id 2}))
                          5 (do (is (= "/bar/2" url)
                                    "replace-state")
                                (.back js/window.history))
                          6 (do (is (= "/" url)
                                    "go back after replace state")

                                ;; Reset to ensure old event listeners aren't called
                                (rfe/start! router
                                            (fn on-navigate [match history]
                                              (let [url (rfh/-get-path history)]
                                                (case (swap! n inc)
                                                  7 (do (is (= "/" url)
                                                            "start at root")
                                                        (rfe/push-state ::foo))
                                                  8 (do (is (= "/foo" url)
                                                            "push-state")
                                                        (rfh/stop! @rfe/history)
                                                        (done))
                                                  (do
                                                    (is false (str "extra event 2" {:n @n, :url url}))
                                                    (done)))))
                                            {:use-fragment true}))
                          (do
                            (is false (str "extra event 1" {:n @n, :url url}))
                            (done)))))
                    {:use-fragment true})))))
