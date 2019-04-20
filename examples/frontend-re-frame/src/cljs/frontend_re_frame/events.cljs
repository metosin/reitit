(ns frontend-re-frame.events
  (:require
   [re-frame.core :as re-frame]
   [frontend-re-frame.db :as db]))

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(re-frame/reg-event-fx
 ::navigate
 (fn [db [_ route]]
   ;; See `navigate` effect in routes.cljs
   {:frontend-re-frame.routes/navigate route}))

(re-frame/reg-event-db
 ::navigated
 (fn [db [_ new-match]]
   (assoc db :current-route new-match)))
