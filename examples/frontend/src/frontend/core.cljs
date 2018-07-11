(ns frontend.core
    (:require [reagent.core :as r]
              [reitit.core :as re]
              [reitit.frontend :as rf]
              [reitit.frontend.history :as rfh]
              [reitit.coercion :as rc]
              [reitit.coercion.schema :as rsc]
              [schema.core :as s]
              [fipp.edn :as fedn]))

(def router (atom nil))

(defn home-page []
  [:div
   [:h2 "Welcome to frontend"]])

(defn about-page []
  [:div
   [:h2 "About frontend"]
   [:a {:href "http://google.com"} "external link"]])

(defn item-page [match]
  (let [{:keys [path query]} (:parameters match)
        {:keys [id]} path]
    [:div
     [:h2 "Selected item " id]
     (if (:foo query)
       [:p "Optional foo query param: " (:foo query)])]))

(defonce match (r/atom nil))

(defn current-page []
  [:div
   [:ul
    [:li [:a {:href (rfh/href @router ::frontpage)} "Frontpage"]]
    [:li [:a {:href (rfh/href @router ::about)} "About"]]
    [:li [:a {:href (rfh/href @router ::item {:id 1})} "Item 1"]]
    [:li [:a {:href (rfh/href @router ::item {:id 2} {:foo "bar"})} "Item 2"]]]
   (if @match
     (let [view (:view (:data @match))]
       [view @match]))
   [:pre (with-out-str (fedn/pprint @match))]])

(def routes
  (re/router
    ["/"
     [""
      {:name ::frontpage
       :view home-page}]
     ["about"
      {:name ::about
       :view about-page}]
     ["item/:id"
      {:name ::item
       :view item-page
       :parameters {:path {:id s/Int}
                    :query {(s/optional-key :foo) s/Keyword}}}]]
    {:compile rc/compile-request-coercers
     :data {:coercion rsc/coercion}}))

(defn init! []
  (reset! router (rfh/start! routes
                             (fn [m] (reset! match m))
                             {:use-fragment true
                              :path-prefix "/"}))
  (r/render [current-page] (.getElementById js/document "app")))

(init!)
