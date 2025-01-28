(ns reitit.frontend.easy-test
  (:require [clojure.string :as str]
            [clojure.test :refer [are async deftest is testing]]
            [goog.events :as gevents]
            [reitit.coercion.malli :as rcm]
            [reitit.core :as r]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.history :as rfh]))

(def browser (exists? js/window))

(def router (r/router ["/"
                       ["" ::frontpage]
                       ["foo" ::foo]
                       ["bar/:id"
                        {:name ::bar
                         :coercion rcm/coercion
                         :parameters {:query [:map
                                              [:q {:optional true}
                                               [:keyword
                                                {:decode/string (fn [s]
                                                                  (if (string? s)
                                                                    (keyword (if (str/starts-with? s "__")
                                                                               (subs s 2)
                                                                               s))
                                                                    s))
                                                 :encode/string (fn [k] (str "__" (name k)))}]]]}}]]))

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
                          1 (rfh/push-state history ::frontpage)
                          2 (do (is (some? (:popstate-listener history)))
                                (is (= "/" url)
                                    "start at root")
                                (rfe/push-state ::foo nil {:a 1} "foo bar"))
                          ;; 0. /
                          ;; 1. /foo?a=1#foo+bar
                          3 (do (is (= "/foo?a=1#foo+bar" url)
                                    "push-state")
                                (.back js/window.history))
                          ;; 0. /
                          4 (do (is (= "/" url)
                                    "go back")
                                (rfe/navigate ::bar {:path-params {:id 1}
                                                     :query-params {:q "x"}}))
                          ;; 0. /
                          ;; 1. /bar/1
                          5 (do (is (= "/bar/1?q=__x" url)
                                    "push-state 2")
                                (rfe/replace-state ::bar {:id 2}))
                          ;; 0. /
                          ;; 1. /bar/2
                          6 (do (is (= "/bar/2" url)
                                    "replace-state")
                                (rfe/set-query {:a 1}))
                          ;; 0. /
                          ;; 1. /bar/2
                          ;; 2. /bar/2?a=1
                          7 (do (is (= "/bar/2?a=1" url)
                                    "update-query with map")
                                (rfe/set-query #(assoc % :q "x") {:replace true}))
                          ;; 0. /
                          ;; 1. /bar/2
                          ;; 2. /bar/2?a=1&b=foo
                          8 (do (is (= "/bar/2?a=1&q=__x" url)
                                    "update-query with fn")
                                (.go js/window.history -2))

                          ;; Go to non-matching path and check set-query works
                          ;; (without coercion) without a match
                          9 (do (is (= "/" url) "go back two events")
                                (.pushState js/window.history nil "" "#/non-matching-path"))

                          10 (do (is (= "/non-matching-path" url))
                                (rfe/set-query #(assoc % :q "x")))

                          11 (do (is (= "/non-matching-path?q=x" url))
                                 (.go js/window.history -2))

                          ;; 0. /
                          12 (do (is (= "/" url)
                                     "go back two events")

                                 ;; Reset to ensure old event listeners aren't called
                                 (rfe/start! router
                                             (fn on-navigate [match history]
                                               (let [url (rfh/-get-path history)]
                                                 (case (swap! n inc)
                                                   13 (do (is (= "/" url)
                                                              "start at root")
                                                          (rfe/push-state ::foo))
                                                   14 (do (is (= "/foo" url)
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
