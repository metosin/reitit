(ns frontend-re-frame.views
  (:require
   [frontend-re-frame.events :as events]
   [frontend-re-frame.routes :as routes]
   [frontend-re-frame.subs :as subs]
   [re-frame.core :as re-frame]))

(defn home-page []
  [:div
   [:h1 "This is home page"]
   [:button
    ;; Dispatch navigate event that triggers a (side)effect.
    {:on-click #(re-frame/dispatch [::events/navigate ::routes/sub-page2])}
    "Go to sub-page 2"]])

(defn sub-page1 []
  [:div
   [:h1 "This is sub-page 1"]])

(defn sub-page2 []
  [:div
   [:h1 "This is sub-page 2"]])

(defn nav []
  (let [current-route (re-frame/subscribe [::subs/current-route])]
    [:div
     (into
      [:ul]
      (for [route (->> routes/routes (filter vector?) (map second))
            :let  [text (-> route :human-name)]]
        [:li
         (when (= (:name route) (-> @current-route :data :name))
           "> ")
         ;; Create a normal links that user can click
         [:a {:href (routes/href (:name route))} text]]))]))

(defn main-panel []
  (let [current-route (re-frame/subscribe [::subs/current-route])]
    [:div
     [nav]
     (condp = (-> @current-route :data :name)
       ::routes/home      [home-page]
       ::routes/sub-page1 [sub-page1]
       ::routes/sub-page2 [sub-page2]
       [:div
        [:p (str "Unknown page")]
        [:pre @current-route]])]))
