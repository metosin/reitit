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
            ;; Elements appended to the document, removed when the test is done.
            appended (atom [])
            append! (fn [el] (swap! appended conj el) (.appendChild js/document.body el))
            history (rfh/start! router (fn [_ history]
                                         (when @clicks
                                           (if (seq @clicks)
                                             (click-next)
                                             (do
                                               (rfh/stop! history)
                                               (doseq [el @appended] (.remove el))
                                               (done)))))
                                {:use-fragment false})
            create-link #(doto
                          (js/document.createElement "A")
                           (.setAttribute "href" (rfh/href history ::foo)))
            document-link (create-link)
            shadow-link (create-link)
            ;; A link in the document that contains a shadow host,
            ;; e.g. a web component. The click starts inside the shadow tree.
            host-link (create-link)
            host-element (js/document.createElement "SPAN")
            host-shadow-root (.attachShadow host-element #js {:mode "open"})
            host-shadow-content (js/document.createElement "SPAN")
            ;; A link in the document containing a shadow host whose shadow tree
            ;; contains another shadow host. The click starts in the inner shadow tree.
            nested-link (create-link)
            outer-host (js/document.createElement "SPAN")
            outer-shadow-root (.attachShadow outer-host #js {:mode "open"})
            inner-host (js/document.createElement "SPAN")
            inner-shadow-root (.attachShadow inner-host #js {:mode "open"})
            inner-shadow-content (js/document.createElement "SPAN")
            ;; A web component whose shadow tree is a link wrapping a slot,
            ;; e.g. <my-link><span>Foo</span></my-link>. The click starts on the
            ;; slotted light DOM content, which is not a DOM descendant of the link.
            slot-host (js/document.createElement "DIV")
            slot-shadow-root (.attachShadow slot-host #js {:mode "open"})
            slot-link (create-link)
            slotted-content (js/document.createElement "SPAN")]
        (append! document-link)

        (append! shadow-element)
        (.appendChild shadow-root shadow-link)

        (append! host-link)
        (.appendChild host-link host-element)
        (.appendChild host-shadow-root host-shadow-content)

        (append! nested-link)
        (.appendChild nested-link outer-host)
        (.appendChild outer-shadow-root inner-host)
        (.appendChild inner-shadow-root inner-shadow-content)

        (append! slot-host)
        (.appendChild slot-shadow-root slot-link)
        (.appendChild slot-link (js/document.createElement "SLOT"))
        (.appendChild slot-host slotted-content)

        (reset! clicks [document-link shadow-link host-shadow-content
                        inner-shadow-content slotted-content])
        (click-next)))))

(deftest html5-history-default-prevented-link-click-test
  (when browser
    (gevents/removeAll js/window goog.events.EventType.POPSTATE)
    (gevents/removeAll js/window goog.events.EventType.HASHCHANGE)
    (gevents/removeAll js/document goog.events.EventType.CLICK)

    (let [history (rfh/start! router (fn [_ _]) {:use-fragment false})
          ;; Start somewhere else than the link target, so that a handled click
          ;; would show up as a change of path.
          _ (rfh/push-state history ::frontpage)
          path-before (rfh/-get-path history)
          link (doto (js/document.createElement "A")
                 (.setAttribute "href" (rfh/href history ::foo))
                 (.addEventListener "click" (fn [e] (.preventDefault e))))]
      (.appendChild js/document.body link)
      (.click link)
      (is (= path-before (rfh/-get-path history))
          "link click is not handled when its default action is prevented")
      (.remove link)
      (rfh/stop! history))))
