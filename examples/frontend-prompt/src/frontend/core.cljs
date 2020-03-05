(ns frontend.core
  (:require [fipp.edn :as fedn]
            [reagent.core :as r]
            [reitit.coercion.spec :as rss]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

;; Implementing conditional prompt on navigation with Reitit frontend.

(defn home-page []
  [:div
   [:h2 "Home"]
   [:p "You will not be prompted to leave this page"]])

(defn prompt-page []
  [:div
   [:h2 "Prompt"]
   [:p "You will be prompted to leave this page"]])

(def routes
  [["/"
    {:name ::home
     :view home-page}]

   ["/prompt"
    {:name   ::prompt
     :view   prompt-page
     ;; Routes can contain arbitrary keys so we add custom :prompt
     ;; key here. See how it's handled in `on-navigate` function.
     :prompt "Are you sure you want to leave?"
     ;; It would be possible to define a function here that resolves
     ;; whether prompting is needed or not but we'll keep it simple.
     }]])

(def router
  (rf/router routes {:data {:coercion rss/coercion}}))

(defonce current-match (r/atom nil))

(defn current-page []
  [:div

   [:ul
    [:li [:a {:href (rfe/href ::home)} "Home"]]
    [:li [:a {:href (rfe/href ::prompt)} "Prompt page"]]]

   (if @current-match
     (let [view   (-> @current-match :data :view)]
       [view @current-match]))
   [:pre (with-out-str (fedn/pprint @current-match))]])

(defn on-navigate [m]
  (if-let [prompt (and (not= @current-match m)
                       (-> @current-match :data :prompt))]
    (if (js/window.confirm prompt) ;; Returns true if OK is pressed.
      (reset! current-match m)
      (.back js/window.history)) ;; Restore browser location
    (reset! current-match m)))

(defn init! []
  (rfe/start!
    router
    on-navigate
    ;; set to false to enable HistoryAPI
    {:use-fragment true})
  (r/render [current-page] (.getElementById js/document "app")))

(init!)
