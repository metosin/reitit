(ns frontend.core
    (:require [reagent.core :as r]
              [reitit.core :as rc]
              [reitit.frontend :as rf]
              [reitit.frontend.history :as rfh]
              [reitit.coercion.schema :as rs]))

(def router (atom nil))

(defn home-page []
  [:div
   [:h2 "Welcome to frontend"]
   [:div [:a {:href (rfh/href @router ::about)} "go to about page"]]])

(defn about-page []
  [:div
   [:h2 "About frontend"]
   [:a {:href "http://google.com"} "external link"]
   [:div [:a {:href (rfh/href @router ::frontpage)} "go to the home page"]]])

(defonce match (r/atom nil))

(defn current-page []
  [:div
   (if @match
     [:div [(:view (:data @match))]])
   (pr-str @match)])

(def routes
  (rc/router
    ["/"
     [""
      {:name ::frontpage
       :view home-page}]
     ["about"
      {:name ::about
       :view about-page}]]))

(defn init! []
  (reset! router (rfh/start! routes
                             (fn [m] (reset! match m))
                             {:use-fragment true
                              :path-prefix "/"}))
  (r/render [current-page] (.getElementById js/document "app")))

(init!)
