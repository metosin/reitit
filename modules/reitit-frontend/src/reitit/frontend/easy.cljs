(ns reitit.frontend.easy
  "Easy wrapper over reitit.frontend.history,
  handling the state. Only one router can be active
  at a time."
  (:require [reitit.frontend.history :as rfh]))

(defonce history (atom nil))

(defn start!
  [routes on-navigate opts]
  (swap! history (fn [old-history]
                   (rfh/stop! old-history)
                   (rfh/start! routes on-navigate opts))))

(defn href
  ([k]
   (rfh/href @history k))
  ([k params]
   (rfh/href @history k params))
  ([k params query]
   (rfh/href @history k params query)))

(defn set-token
  "Sets the new route, leaving previous route in history."
  ([k]
   (rfh/set-token @history k))
  ([k params]
   (rfh/set-token @history k params))
  ([k params query]
   (rfh/set-token @history k params query)))

(defn replace-token
  "Replaces current route. I.e. current route is not left on history."
  ([k]
   (rfh/replace-token @history k))
  ([k params]
   (rfh/replace-token @history k params))
  ([k params query]
   (rfh/replace-token @history k params query)))
