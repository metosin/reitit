(ns reitit.frontend.history-test
  (:require [clojure.test :refer [deftest testing is are async]]
            [reitit.core :as r]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.test-utils :refer [capture-console]]
            [goog.events :as gevents]
            [reitit.coercion.malli :as rcm]))

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
                                                {:decode/string (fn [s] (keyword (subs s 2)))
                                                 :encode/string (fn [k] (str "__" (name k)))}]]]}}]]))

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
        (testing "query string coercion doesn't strip extra keys"
          (is (= "#/bar/5?extra=a"
                 (rfh/href history ::bar {:id 5} {:extra "a"}))))
        (is (= "#/bar/5?q=__x"
               (rfh/href history ::bar {:id 5} {:q "x"})))
        (is (= "#/bar/5?q=__x#foo"
               (rfh/href history ::bar {:id 5} {:q "x"} "foo")))
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
      (let [n (atom 0)]
        (rfh/start! router
                    (fn [match history]
                      (let [url (rfh/-get-path history)]
                        (case (swap! n inc)
                          1 (rfh/push-state history ::frontpage)
                          2 (do (is (= "/" url)
                                    "start at root")
                                (rfh/push-state history ::foo))
                          3 (do (is (= "/foo" url)
                                    "push-state")
                                (.back js/window.history))
                          4 (do (is (= "/" url)
                                    "go back")
                                (rfh/push-state history ::bar {:id 1} {:extra "a"}))
                          5 (do (is (= "/bar/1?extra=a" url)
                                    "push-state 2")
                                (rfh/replace-state history ::bar {:id 2} {:q "x"}))
                          6 (do (is (= "/bar/2?q=__x" url)
                                    "replace-state")
                                (.back js/window.history))
                          7 (do (is (= "/" url)
                                    "go back after replace state")
                                (rfh/stop! history)
                                (done))
                          (is false "extra event"))))
                    {:use-fragment true})))))

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
        (is (= "/bar/5?q=__x"
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
      (let [n (atom 0)]
        (rfh/start! router
                    (fn [match history]
                      (let [url (rfh/-get-path history)]
                        (case (swap! n inc)
                          1 (rfh/push-state history ::frontpage)
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
                                (rfh/replace-state history ::bar {:id 2} {:q "x"}))
                          6 (do (is (= "/bar/2?q=__x" url)
                                    "replace-state")
                                (.back js/window.history))
                          7 (do (is (= "/" url)
                                    "go back after replace state")
                                (rfh/stop! history)
                                (done))
                          (is false "extra event"))))
                    {:use-fragment false})))))

(deftest html5-history-link-click-test
  (when browser
    (gevents/removeAll js/window goog.events.EventType.POPSTATE)
    (gevents/removeAll js/window goog.events.EventType.HASHCHANGE)
    (gevents/removeAll js/document goog.events.EventType.CLICK)

    ;; Will fail with "Some of your tests did a full page reload!"
    ;; if link events are not handled

    (async done
      (let [clicks (atom nil)
            click-next (fn []
                         (let [target (first @clicks)]
                           (swap! clicks rest)
                           (.click target)))
            shadow-element (js/document.createElement "DIV")
            shadow-root (.attachShadow shadow-element #js {:mode "open"})
            history (rfh/start! router (fn [_ history]
                                         (when @clicks
                                           (if (seq @clicks)
                                             (click-next)
                                             (do
                                               (rfh/stop! history)
                                               (done)))))
                                {:use-fragment false})
            create-link #(doto
                          (js/document.createElement "A")
                           (.setAttribute "href" (rfh/href history ::foo)))
            document-link (create-link)
            shadow-link (create-link)]
        (.appendChild js/document.body document-link)

        (.appendChild js/document.body shadow-element)
        (.appendChild shadow-root shadow-link)

        (reset! clicks [document-link shadow-link])
        (click-next)))))
