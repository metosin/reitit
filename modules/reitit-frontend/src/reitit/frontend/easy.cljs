(ns reitit.frontend.easy
  "Easy wrapper over reitit.frontend.history,
  handling the state. Only one router can be active
  at a time."
  (:require [reitit.frontend.history :as rfh]))

(defonce history (atom nil))

;; Doc-strings from reitit.frontend.history
;; remember to update both!

(defn start!
  "This registers event listeners on HTML5 history and hashchange events.
  When using with development workflow like Figwheel, rememeber to
  remove listeners using stop! call before calling start! again.

  Parameters:
  - router         The Reitit router.
  - on-navigate    Function to be called when route changes. Takes two parameters, ´match´ and ´history´ object.

  Options:
  - :use-fragment  (default true) If true, onhashchange and location hash are used to store the token."
  [router on-navigate opts]
  (swap! history (fn [old-history]
                   (rfh/stop! old-history)
                   (rfh/start! router on-navigate opts))))

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
