(ns frontend.core
    (:require [reagent.core :as r]
              [reitit.frontend :as rf]
              [reitit.frontend.easy :as rfe]
              [reitit.frontend.controllers :as rfc]
              [reitit.coercion.schema :as rsc]
              [schema.core :as s]
              [fipp.edn :as fedn]))

(defonce state (r/atom {:user nil}))

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

(defn login-done [user]
  (swap! state assoc :user user))

(defn login [user]
  ;; In real app one would send API call here to create session or retrieve token or something
  ;; and the callback would update app-state
  (js/setTimeout #(login-done user) 250))

(defn logout []
  (swap! state assoc :user nil))

(defn login-view []
  (let [form (r/atom {})]
    (fn []
      [:div
       [:form
        {:on-submit (fn [e]
                      (.preventDefault e)
                      (if (and (:username @form)
                               (:password @form))
                        (login @form)))}

        [:label "Username"]
        [:input
         {:default-value ""
          :on-change #(swap! form assoc :username %)}]

        [:label "Password"]
        [:input
         {:default-value ""
          :on-change #(swap! form assoc :password %)}]

        [:button
         {:type "submit"}
         "Login"]]])))

(defn about-page []
  [:div
   [:p "This view is public."]])

(defn main-view []
  (let [{:keys [user match]} @state
        route-data (:data match)]
    [:div
     [:ul
      [:li [:a {:href (rfe/href ::frontpage)} "Frontpage"]]
      [:li [:a {:href (rfe/href ::about)} "About (public)"]]
      [:li [:a {:href (rfe/href ::item-list)} "Item list"]]
      (if user
        [:li [:a {:on-click (fn [e]
                              (.preventDefault e)
                              (logout))
                  :href "#"}
              "Logout"]])]
     ;; If user is authenticated
     ;; or if this route has been defined as public, else login view
     (if (or user
             (:public? route-data))
       (if match
         (let [view (:view route-data)]
           [view match]))
       [login-view])
     [:pre (with-out-str (fedn/pprint @state))]]))

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

     ["about"
      {:name ::about
       :view about-page
       :public? true}]

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
        :controllers [{:params (fn [match]
                                 (:path (:parameters match)))
                       :start (fn [params]
                                (js/console.log "start" "item controller" (:id params)))
                       :stop (fn [params]
                               (js/console.log "stop" "item controller" (:id params)))}]}]]]
    {:data {:controllers [{:start (log-fn "start" "root-controller")
                           :stop (log-fn "stop" "root controller")}]
            :coercion rsc/coercion
            :public? false}}))

(defn init! []
  (rfe/start!
    routes
    (fn [new-match]
      (swap! state (fn [state]
                     (if new-match
                       ;; Only run the controllers, which are likely to call authenticated APIs,
                       ;; if user has been authenticated.
                       ;; Alternative solution could be to always run controllers,
                       ;; check authentication status in each controller, or check authentication status in API calls.
                       (if (:user state)
                         (assoc state :match (assoc new-match :controllers (rfc/apply-controllers (:controllers (:match state)) new-match)))
                         (assoc state :match new-match))))))
    {:use-fragment true})
  (r/render [main-view] (.getElementById js/document "app")))

(init!)
