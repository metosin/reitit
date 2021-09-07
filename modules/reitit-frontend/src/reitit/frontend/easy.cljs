(ns reitit.frontend.easy
  "Easy wrapper over reitit.frontend.history,
  handling the state. Only one router can be active
  at a time."
  (:require [reitit.frontend.history :as rfh]))

(defonce history (atom nil))

;; Doc-strings from reitit.frontend.history remember to update both!
;; Differences:
;; This one automatically removes previous event listeners.

(defn ^{:see-also ["reitit.frontend.history/start!"]}
  start!
  "This registers event listeners on HTML5 history and hashchange events.

  Automatically removes previous event listeners so it is safe to call this repeatedly, for example when using
  Figwheel or similar development workflow.

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
  [router on-navigate opts]
  ;; Stop and set to nil.
  (swap! history rfh/stop!)
  ; ;; Store the reference to History object in navigate callback, before calling user
  ; ;; callback, so that user function can call rfe functions.
  (rfh/start! router (fn rfe-on-navigate [m this]
                       (when (nil? @history)
                         (reset! history this))
                       (on-navigate m this))
    opts))

(defn
  ^{:see-also ["reitit.frontend.history/href"]}
  href
  "Generate link href for the given route and parameters

  Note: currently collections in query-parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\", if you want to encode them
  differently, convert the collections to strings first."
  ([name]
   (rfh/href @history name nil nil))
  ([name path-params]
   (rfh/href @history name path-params nil))
  ([name path-params query-params]
   (rfh/href @history name path-params query-params)))

(defn
  ^{:see-also ["reitit.frontend.history/push-state"]}
  push-state
  "Creates url using the given route and parameters, pushes those to
  history stack with pushState and triggers on-navigate callback on the
  history handler.

  Note: currently collections in query-parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\", if you want to encode them
  differently, convert the collections to strings first.

  See also:
  https://developer.mozilla.org/en-US/docs/Web/API/History/pushState"
  ([name]
   (rfh/push-state @history name nil nil))
  ([name path-params]
   (rfh/push-state @history name path-params nil))
  ([name path-params query-params]
   (rfh/push-state @history name path-params query-params)))

(defn
  ^{:see-also ["reitit.frontend.history/replace-state"]}
  replace-state
  "Creates url using the given route and parameters, replaces latest entry on
  history stack with replaceState and triggers on-navigate callback on the
  history handler.

  Note: currently collections in query-parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\", if you want to encode them
  differently, convert the collections to strings first.

  See also:
  https://developer.mozilla.org/en-US/docs/Web/API/History/replaceState"
  ([name]
   (rfh/replace-state @history name nil nil))
  ([name path-params]
   (rfh/replace-state @history name path-params nil))
  ([name path-params query-params]
   (rfh/replace-state @history name path-params query-params)))
