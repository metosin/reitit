(ns reitit.frontend.history
  "Provides integration to hash-change or HTML5 History
  events."
  (:require [goog.events :as gevents]
            [reitit.core :as reitit]
            [reitit.frontend :as rf]
            goog.Uri))

(defprotocol History
  (-init [this] "Create event listeners")
  (-stop [this] "Remove event listeners")
  (-on-navigate [this path] "Find a match for current routing path and call on-navigate callback")
  (-get-path [this] "Get the current routing path, including query string and fragment")
  (-href [this path] "Converts given routing path to browser location"))

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
                        (-on-navigate this path))))
          ;; rfe start! uses first on-navigate call to store the
          ;; instance so it has to see the instance with listeners.
          this (assoc this
                      :popstate-listener (gevents/listen js/window goog.events.EventType.POPSTATE handler false)
                      :hashchange-listener (gevents/listen js/window goog.events.EventType.HASHCHANGE handler false))]
      (-on-navigate this (-get-path this))
      this))
  (-stop [this]
    (gevents/unlistenByKey popstate-listener)
    (gevents/unlistenByKey hashchange-listener)
    nil)
  (-on-navigate [this path]
    (reset! last-fragment path)
    (on-navigate (rf/match-by-path router path this) this))
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

(defn- event-target
  "Read event's target from composed path to get shadow dom working,
  fallback to target property if not available"
  [^goog.events.BrowserEvent event]
  (let [original-event (.getBrowserEvent event)]
    (if (exists? (.-composedPath original-event))
      (aget (.composedPath original-event) 0)
      (.-target event))))

(defn ignore-anchor-click?
  "Precicate to check if the anchor click event default action
  should be ignored. This logic will ignore the event
  if anchor href matches the route tree, and in this case
  the page location is updated using History API."
  [router e el ^goog.Uri uri]
  (let [current-domain (if (exists? js/location)
                         (.getDomain ^goog.Uri (.parse goog.Uri js/location)))]
    (and (or (and (not (.hasScheme uri)) (not (.hasDomain uri)))
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
         ;; NOTE: Why doesn't this use frontend variant instead of core?
         (reitit/match-by-path router (.getPath uri)))))

(defrecord Html5History [on-navigate router listen-key click-listen-key]
  History
  (-init [this]
    (let [handler
          (fn [e]
            (-on-navigate this (-get-path this)))

          ignore-anchor-click-predicate (or (:ignore-anchor-click? this)
                                            ignore-anchor-click?)

          ;; Prevent document load when clicking a elements, if the href points to URL that is part
          ;; of the routing tree."
          ignore-anchor-click (fn [e]
                                ;; Returns the next matching ancestor of event target
                                (when-let [el (closest-by-tag (event-target e) "a")]
                                  (let [^goog.Uri uri (.parse goog.Uri (.-href el))]
                                    (when (ignore-anchor-click-predicate router e el uri)
                                      (.preventDefault e)
                                      (let [path (str (.getPath uri)
                                                      (when (.hasQuery uri)
                                                        (str "?" (.getQuery uri)))
                                                      (when (.hasFragment uri)
                                                        (str "#" (.getFragment uri))))]
                                        (.pushState js/window.history nil "" path)
                                        (-on-navigate this path))))))
          this (assoc this
                      :listen-key (gevents/listen js/window goog.events.EventType.POPSTATE handler false)
                      :click-listen-key (gevents/listen js/document goog.events.EventType.CLICK ignore-anchor-click))]
      (-on-navigate this (-get-path this))
      this))
  (-on-navigate [this path]
    (on-navigate (rf/match-by-path router path this) this))
  (-stop [this]
    (gevents/unlistenByKey listen-key)
    (gevents/unlistenByKey click-listen-key)
    nil)
  (-get-path [this]
    (str (.. js/window -location -pathname)
         (.. js/window -location -search)
         (.. js/window -location -hash)))
  (-href [this path]
    path))

(defn start!
  "This registers event listeners on HTML5 history and hashchange events.

  Returns History object.

  When using with development workflow like Figwheel, remember to
  remove listeners using stop! call before calling start! again.

  Parameters:
  - router         The Reitit router.
  - on-navigate    Function to be called when route changes. Takes two parameters, ´match´ and ´history´ object.

  Options:
  - :use-fragment  (default true) If true, onhashchange and location hash are used to store current route.

  Options (Html5History):
  - :ignore-anchor-click?  Function (router, event, anchor element, uri) which will be called to
                           check if the anchor click event should be ignored.
                           To extend built-in check, you can call `reitit.frontend.history/ignore-anchor-click?`
                           function, which will ignore clicks if the href matches route tree."
  ([router on-navigate]
   (start! router on-navigate nil))
  ([router
    on-navigate
    {:keys [use-fragment]
     :or {use-fragment true}
     :as opts}]
   (let [opts (-> opts
                  (dissoc :use-fragment)
                  (assoc :router router
                         :on-navigate on-navigate))]
     (-init (if use-fragment
              (map->FragmentHistory opts)
              (map->Html5History opts))))))

(defn stop!
  "Stops the given history handler, removing the event handlers."
  [history]
  (if history
    (-stop history)))

(defn
  ^{:see-also ["reitit.core/match->path"]}
  href
  "Generate a URL for a route defined by name, with given path-params and query-params.

  The URL is formatted using Reitit frontend history handler, so using it with
  anchor element href will correctly trigger route change event.

  By default currently collections in query parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\". To encode them differently, you can
  either use Malli coercion to encode values, or just turn the values to strings
  before calling the function."
  ([history name]
   (href history name nil))
  ([history name path-params]
   (href history name path-params nil))
  ([history name path-params query-params]
   (href history name path-params query-params nil))
  ([history name path-params query-params fragment]
   (let [match (rf/match-by-name! (:router history) name path-params)]
     (-href history (rf/match->path match query-params fragment)))))

(defn
  ^{:see-also ["reitit.core/match->path"]}
  push-state
  "Updates the browser URL and pushes new entry to the history stack using
  a route defined by name, with given path-params and query-params.

  Will also trigger on-navigate callback on Reitit frontend History handler.

  By default currently collections in query parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\". To encode them differently, you can
  either use Malli coercion to encode values, or just turn the values to strings
  before calling the function.

  See also:
  https://developer.mozilla.org/en-US/docs/Web/API/History/pushState"
  ([history name]
   (push-state history name nil nil nil))
  ([history name path-params]
   (push-state history name path-params nil nil))
  ([history name path-params query-params]
   (push-state history name path-params query-params nil))
  ([history name path-params query-params fragment]
   (let [match (rf/match-by-name! (:router history) name path-params)
         path (rf/match->path match query-params fragment)]
     ;; pushState and replaceState don't trigger popstate event so call on-navigate manually
     (.pushState js/window.history nil "" (-href history path))
     (-on-navigate history path))))

(defn
  ^{:see-also ["reitit.core/match->path"]}
  replace-state
  "Updates the browser location and replaces latest entry in the history stack
  using URL built from a route defined by name, with given path-params and
  query-params.

  Will also trigger on-navigate callback on Reitit frontend History handler.

  By default currently collections in query parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\". To encode them differently, you can
  either use Malli coercion to encode values, or just turn the values to strings
  before calling the function.

  See also:
  https://developer.mozilla.org/en-US/docs/Web/API/History/replaceState"
  ([history name]
   (replace-state history name nil nil nil))
  ([history name path-params]
   (replace-state history name path-params nil nil))
  ([history name path-params query-params]
   (replace-state history name path-params query-params nil))
  ([history name path-params query-params fragment]
   (let [match (rf/match-by-name! (:router history) name path-params)
         path (rf/match->path match query-params fragment)]
     (.replaceState js/window.history nil "" (-href history path))
     (-on-navigate history path))))

(defn
  ^{:see-also ["reitit.core/match->path"]}
  navigate
  "Updates the browser location and either pushes new entry to the history stack
  or replaces the latest entry in the the history stack (controlled by
  `replace` option) using URL built from a route defined by name given
  parameters.

  Will also trigger on-navigate callback on Reitit frontend History handler.

  By default currently collections in query parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\". To encode them differently, you can
  either use Malli coercion to encode values, or just turn the values to strings
  before calling the function.

  See also:
  https://developer.mozilla.org/en-US/docs/Web/API/History/pushState
  https://developer.mozilla.org/en-US/docs/Web/API/History/replaceState"
  ([history name]
   (navigate history name nil))
  ([history name {:keys [path-params query-params fragment replace] :as opts}]
   (let [match (rf/match-by-name! (:router history) name path-params)
         path (rf/match->path match query-params fragment)]
     (if replace
       (.replaceState js/window.history nil "" (-href history path))
       (.pushState js/window.history nil "" (-href history path)))
     (-on-navigate history path))))

(defn
  ^{:see-also ["reitit.frontend/set-query-params"]}
  set-query
  "Update query parameters for the current route.

  New query params can be given as a map, or a function taking
  the old params and returning the new modified params.

  The current path is matched against the routing tree, and the match data
  (schema, coercion) is used to encode the query parameters.
  If the current path doesn't match any route, the query parameters
  are parsed from the path without coercion and new values
  are also stored without coercion encoding."
  ([history new-query-or-update-fn]
   (set-query history new-query-or-update-fn nil))
  ([history new-query-or-update-fn {:keys [replace] :as opts}]
   (let [current-path (-get-path history)
         match (rf/match-by-path (:router history) current-path)
         new-path (if match
                    (let [query-params (if (fn? new-query-or-update-fn)
                                         (new-query-or-update-fn (:query (:parameters match)))
                                         new-query-or-update-fn)]
                      (rf/match->path match query-params (:fragment (:parameters match))))
                    (rf/set-query-params current-path new-query-or-update-fn))]
     (if replace
       (.replaceState js/window.history nil "" (-href history new-path))
       (.pushState js/window.history nil "" (-href history new-path)))
     (-on-navigate history new-path))))
