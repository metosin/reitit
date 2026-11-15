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
  "Generate a URL for a route defined by name, with given path-params and query-params.

  The URL is formatted using Reitit frontend history handler, so using it with
  anchor element href will correctly trigger route change event.

  By default currently collections in query parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\". To encode them differently, you can
  either use Malli coercion to encode values, or just turn the values to strings
  before calling the function."
  ([name]
   (rfh/href @history name nil nil nil))
  ([name path-params]
   (rfh/href @history name path-params nil nil))
  ([name path-params query-params]
   (rfh/href @history name path-params query-params nil))
  ([name path-params query-params fragment]
   (rfh/href @history name path-params query-params fragment)))

(defn
  ^{:see-also ["reitit.frontend.history/push-state"]}
  push-state
  "Updates the browser location and pushes new entry to the history stack using
  URL built from a route defined by name, with given path-params and
  query-params.

  Will also trigger on-navigate callback on Reitit frontend History handler.

  By default currently collections in query parameters are encoded as field-value
  pairs separated by &, i.e. \"?a=1&a=2\". To encode them differently, you can
  either use Malli coercion to encode values, or just turn the values to strings
  before calling the function.

  See also:
  https://developer.mozilla.org/en-US/docs/Web/API/History/pushState"
  ([name]
   (rfh/push-state @history name nil nil nil))
  ([name path-params]
   (rfh/push-state @history name path-params nil nil))
  ([name path-params query-params]
   (rfh/push-state @history name path-params query-params nil))
  ([name path-params query-params fragment]
   (rfh/push-state @history name path-params query-params fragment)))

(defn
  ^{:see-also ["reitit.frontend.history/replace-state"]}
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
  ([name]
   (rfh/replace-state @history name nil nil nil))
  ([name path-params]
   (rfh/replace-state @history name path-params nil nil))
  ([name path-params query-params]
   (rfh/replace-state @history name path-params query-params nil))
  ([name path-params query-params fragment]
   (rfh/replace-state @history name path-params query-params fragment)))

;; This duplicates previous two, but the map parameter will be easier way to
;; extend the functions, e.g. to work with fragment string. Toggling push vs
;; replace can be also simpler with a flag.
;; Navigate and set-query are also similer to react-router API.
(defn
  ^{:see-also ["reitit.frontend.history/navigate"]}
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
  ([name]
   (rfh/navigate @history name))
  ([name {:keys [path-params query-params replace fragment] :as opts}]
   (rfh/navigate @history name opts)))

(defn
  ^{:see-also ["reitit.frontend.history/set-query"]}
  set-query
  "Update query parameters for the current route.

  New query params can be given as a map, or a function taking
  the old params and returning the new modified params.

  The current path is matched against the routing tree, and the match data
  (schema, coercion) is used to encode the query parameters.
  If the current path doesn't match any route, the query parameters
  are parsed from the path without coercion and new values
  are also stored without coercion encoding."
  ([new-query-or-update-fn]
   (rfh/set-query @history new-query-or-update-fn))
  ([new-query-or-update-fn {:keys [replace] :as opts}]
   (rfh/set-query @history new-query-or-update-fn opts)))
