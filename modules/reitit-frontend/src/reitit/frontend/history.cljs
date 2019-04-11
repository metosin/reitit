(ns reitit.frontend.history
  "Provides integration to hash-change or HTML5 History
  events."
  (:require [reitit.core :as reitit]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [goog.events :as gevents])
  (:import goog.Uri))

(defprotocol History
  (-init [this] "Create event listeners")
  (-stop [this] "Remove event listeners")
  (-on-navigate [this path])
  (-get-path [this])
  (-href [this path]))

;; This version listens for both pop-state and hash-change for
;; compatibility for old browsers not supporting History API.
(defrecord FragmentHistory [on-navigate router popstate-listener hashchange-listener last-fragment]
  History
  (-init [this]
    ;; Link clicks and e.g. back button trigger both events, if fragment is same as previous ignore second event.
    ;; For old browsers only the hash-change event is triggered.
    (let [last-fragment (atom nil)
          this (assoc this :last-fragment last-fragment)
          handler (fn [e]
                    (let [path (-get-path this)]
                      (when (or (= goog.events.EventType.POPSTATE (.-type e))
                                (not= @last-fragment path))
                        (-on-navigate this path))))]
      (-on-navigate this (-get-path this))
      (assoc this
             :popstate-listener (gevents/listen js/window goog.events.EventType.POPSTATE handler false)
             :hashchange-listener (gevents/listen js/window goog.events.EventType.HASHCHANGE handler false))))
  (-stop [this]
    (gevents/unlistenByKey popstate-listener)
    (gevents/unlistenByKey hashchange-listener))
  (-on-navigate [this path]
    (reset! last-fragment path)
    (on-navigate (rf/match-by-path router path) this))
  (-get-path [this]
    ;; Remove #
    ;; "" or "#" should be same as "#/"
    (let [fragment (subs (.. js/window -location -hash) 1)]
      (if (= "" fragment)
        "/"
        fragment)))
  (-href [this path]
    (if path
      (str "#" path))))

(defn- closest-by-tag [el tag]
  ;; nodeName is upper case for HTML always,
  ;; for XML or XHTML it would be in the original case.
  (let [tag (.toUpperCase tag)]
    (loop [el el]
      (if el
        (if (= tag (.-nodeName el))
          el
          (recur (.-parentNode el)))))))

(defrecord Html5History [on-navigate router listen-key click-listen-key]
  History
  (-init [this]
    (let [handler
          (fn [e]
            (-on-navigate this (-get-path this)))

          current-domain
          (if (exists? js/location)
            (.getDomain (.parse Uri js/location)))

          ;; Prevent document load when clicking a elements, if the href points to URL that is part
          ;; of the routing tree."
          ignore-anchor-click
          (fn ignore-anchor-click
            [e]
            ;; Returns the next matching anchestor of event target
            (when-let [el (closest-by-tag (.-target e) "a")]
              (let [uri (.parse Uri (.-href el))]
                (when (and (or (and (not (.hasScheme uri)) (not (.hasDomain uri)))
                               (= current-domain (.getDomain uri)))
                           (not (.-altKey e))
                           (not (.-ctrlKey e))
                           (not (.-metaKey e))
                           (not (.-shiftKey e))
                           (or (not (.hasAttribute el "target"))
                               (contains? #{"" "_self"} (.getAttribute el "target")))
                           ;; Left button
                           (= 0 (.-button e))
                           ;; isContentEditable property is inherited from parents,
                           ;; so if the anchor is inside contenteditable div, the property will be true.
                           (not (.-isContentEditable el))
                           (reitit/match-by-path router (.getPath uri)))
                  (.preventDefault e)
                  (let [path (str (.getPath uri)
                                  (if (seq (.getQuery uri))
                                    (str "?" (.getQuery uri))))]
                    (.pushState js/window.history nil "" path)
                    (-on-navigate this path))))))]
      (-on-navigate this (-get-path this))
      (assoc this
             :listen-key (gevents/listen js/window goog.events.EventType.POPSTATE handler false)
             :click-listen-key (gevents/listen js/document goog.events.EventType.CLICK ignore-anchor-click))))
  (-on-navigate [this path]
    (on-navigate (rf/match-by-path router path) this))
  (-stop [this]
    (gevents/unlistenByKey listen-key)
    (gevents/unlistenByKey click-listen-key))
  (-get-path [this]
    (str (.. js/window -location -pathname)
         (.. js/window -location -search)))
  (-href [this path]
    path))

(defn start!
  "This registers event listeners on HTML5 history and hashchange events.

  Returns History object.

  When using with development workflow like Figwheel, rememeber to
  remove listeners using stop! call before calling start! again.

  Parameters:
  - router         The Reitit router.
  - on-navigate    Function to be called when route changes. Takes two parameters, ´match´ and ´history´ object.

  Options:
  - :use-fragment  (default true) If true, onhashchange and location hash are used to store current route."
  ([router on-navigate]
   (start! router on-navigate nil))
  ([router
    on-navigate
    {:keys [use-fragment]
     :or {use-fragment true}}]
   (let [opts {:router router
               :on-navigate on-navigate}]
     (-init (if use-fragment
              (map->FragmentHistory opts)
              (map->Html5History opts))))))

(defn stop! [history]
  (if history
    (-stop history)))

(defn href
  ([history k]
   (href history k nil))
  ([history k params]
   (href history k params nil))
  ([history k params query]
   (let [match (rf/match-by-name! (:router history) k params)]
     (-href history (r/match->path match query)))))

(defn push-state
  "Sets the new route, leaving previous route in history."
  ([history k]
   (push-state history k nil nil))
  ([history k params]
   (push-state history k params nil))
  ([history k params query]
   (let [match (rf/match-by-name! (:router history) k params)
         path (r/match->path match query)]
     ;; pushState and replaceState don't trigger popstate event so call on-navigate manually
     (.pushState js/window.history nil "" (-href history path))
     (-on-navigate history path))))

(defn replace-state
  "Replaces current route. I.e. current route is not left on history."
  ([history k]
   (replace-state history k nil nil))
  ([history k params]
   (replace-state history k params nil))
  ([history k params query]
   (let [match (rf/match-by-name! (:router history) k params)
         path (r/match->path match query)]
     (.replaceState js/window.history nil "" (-href history path))
     (-on-navigate history path))))
