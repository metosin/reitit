(ns reitit.frontend.easy
  "Easy wrapper over reitit.frontend.history,
  handling the state. Only one router can be active
  at a time."
  (:require [reitit.frontend.history :as rfh]))

(defonce history (atom nil))

;; Doc-strings from reitit.frontend.history remember to update both!
;; Differences:
;; This one automatically removes previous event listeners.

(defn start!
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

(defn href
  ([k]
   (rfh/href @history k nil nil))
  ([k params]
   (rfh/href @history k params nil))
  ([k params query]
   (rfh/href @history k params query)))

(defn push-state
  "Sets the new route, leaving previous route in history."
  ([k]
   (rfh/push-state @history k nil nil))
  ([k params]
   (rfh/push-state @history k params nil))
  ([k params query]
   (rfh/push-state @history k params query)))

(defn replace-state
  "Replaces current route. I.e. current route is not left on history."
  ([k]
   (rfh/replace-state @history k nil nil))
  ([k params]
   (rfh/replace-state @history k params nil))
  ([k params query]
   (rfh/replace-state @history k params query)))
