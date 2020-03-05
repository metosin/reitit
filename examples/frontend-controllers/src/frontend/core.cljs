(ns frontend.core
    (:require [reagent.core :as r]
              [reitit.frontend :as rf]
              [reitit.frontend.easy :as rfe]
              [reitit.frontend.controllers :as rfc]
              [reitit.coercion.schema :as rsc]
              [schema.core :as s]
              [fipp.edn :as fedn]))

(defn home-page []
  [:div
   [:h2 "Welcome to frontend"]
   [:p "Look at console log for controller calls."]])

(defn item-page [match]
  (let [{:keys [path query]} (:parameters match)
        {:keys [id]} path]
    [:div
     [:ul
      [:li [:a {:href (rfe/href ::item {:id 1})} "Item 1"]]
      [:li [:a {:href (rfe/href ::item {:id 2} {:foo "bar"})} "Item 2"]]]
     (if id
       [:h2 "Selected item " id])
     (if (:foo query)
       [:p "Optional foo query param: " (:foo query)])]))

(defonce match (r/atom nil))

(defn current-page []
  [:div
   [:ul
    [:li [:a {:href (rfe/href ::frontpage)} "Frontpage"]]
    [:li
     [:a {:href (rfe/href ::item-list)} "Item list"]
     ]]
   (if @match
     (let [view (:view (:data @match))]
       [view @match]))
   [:pre (with-out-str (fedn/pprint @match))]])

(defn log-fn [& params]
  (fn [_]
    (apply js/console.log params)))

(def routes
  (rf/router
    ["/"
     [""
      {:name ::frontpage
       :view home-page
       :controllers [{:start (log-fn "start" "frontpage controller")
                      :stop (log-fn "stop" "frontpage controller")}]}]
     ["items"
      ;; Shared data for sub-routes
      {:view item-page
       :controllers [{:start (log-fn "start" "items controller")
                      :stop (log-fn "stop" "items controller")}]}

      [""
       {:name ::item-list
        :controllers [{:start (log-fn "start" "item-list controller")
                       :stop (log-fn "stop" "item-list controller")}]}]
      ["/:id"
       {:name ::item
        :parameters {:path {:id s/Int}
                     :query {(s/optional-key :foo) s/Keyword}}
        :controllers [{:parameters {:path [:id]}
                       :start (fn [{:keys [path]}]
                                (js/console.log "start" "item controller" (:id path)))
                       :stop (fn [{:keys [path]}]
                               (js/console.log "stop" "item controller" (:id path)))}]}]]]
    {:data {:controllers [{:start (log-fn "start" "root-controller")
                           :stop (log-fn "stop" "root controller")}]
            :coercion rsc/coercion}}))

(defn init! []
  (rfe/start!
    routes
    (fn [new-match]
      (swap! match (fn [old-match]
                     (if new-match
                       (assoc new-match :controllers (rfc/apply-controllers (:controllers old-match) new-match))))))
    {:use-fragment true})
  (r/render [current-page] (.getElementById js/document "app")))

(init!)
