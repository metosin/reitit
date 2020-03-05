(ns frontend.core
  (:require [clojure.string :as string]
            [fipp.edn :as fedn]
            [reagent.core :as r]
            [reitit.coercion.spec :as rss]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [spec-tools.data-spec :as ds]))

;; Components similar to react-router `Link`, `NavLink` and `Redirect`
;; with Reitit frontend.

(defn home-page []
  [:div
   [:h2 "Welcome to frontend"]
   [:p "This is home page"]])

(defn about-page []
  [:div
   [:h2 "About frontend"]
   [:p "This is about page"]])

(defn redirect!
  "If `push` is truthy, previous page will be left in history."
  [{:keys [to path-params query-params push]}]
  (if push
    (rfe/push-state to path-params query-params)
    (rfe/replace-state to path-params query-params)))

(defn Redirect
  "Component that only causes a redirect side-effect."
  [props]
  (r/create-class
   {:component-did-mount  (fn [this] (redirect! (r/props this)))
    :component-did-update (fn [this [_ prev-props]]
                            (if (not= (r/props this) prev-props)
                              (redirect! (r/props this))))
    :render (fn [this] nil)}))

(defn item-page [match]
  (let [{:keys [path query]} (:parameters match)
        {:keys [id]} path]
    (if (< id 1)
      [Redirect {:to ::frontpage}]
      [:div
       [:h2 "Selected item " id]
       (when (:foo query)
         [:p "Optional foo query param: " (:foo query)])])))

(def routes
  [["/"
    {:name ::frontpage
     :view home-page}]

   ["/about"
    {:name ::about
     :view about-page}]

   ["/item/:id"
    {:name ::item
     :view item-page
     :parameters
     {:path {:id int?}
      :query {(ds/opt :foo) keyword?}}}]])

(def router
  (rf/router routes {:data {:coercion rss/coercion}}))

(defonce current-match (r/atom nil))

(defn- resolve-href
  [to path-params query-params]
  (if (keyword? to)
    (rfe/href to path-params query-params)
    (let [match  (rf/match-by-path router to)
          route  (-> match :data :name)
          params (or path-params (:path-params match))
          query  (or query-params (:query-params match))]
      (if match
        (rfe/href route params query)
        to))))

(defn Link
  [{:keys [to path-params query-params active]} & children]
  (let [href (resolve-href to path-params query-params)]
    (into
     [:a {:href href} (when active "> ")] ;; Apply styles or whatever
     children)))

(defn- name-matches?
  [name path-params match]
  (and (= name (-> match :data :name))
       (= (not-empty path-params)
          (-> match :parameters :path not-empty))))

(defn- url-matches?
  [url match]
  (= (-> url (string/split #"\?") first)
     (:path match)))

(defn NavLink
  [{:keys [to path-params] :as props} & children]
  (let [active (or (name-matches? to path-params @current-match)
                   (url-matches? to @current-match))]
    [Link (assoc props :active active) children]))

(defn current-page []
  [:div

   [:h4 "Link"]
   [:ul
    [:li [Link {:to ::frontpage} "Frontpage"]]
    [:li [Link {:to "/about"} "About"]]
    [:li [Link {:to ::item :path-params {:id 1}} "Item 1"]]
    [:li [Link {:to "/item/2?foo=bar"} "Item 2"]]
    [:li [Link {:to "/item/-1"} "Item -1 (redirects to frontpage)"]]
    [:li [Link {:to "http://www.google.fi"} "Google"]]]

   [:h4 "NavLink"]
   [:ul
    [:li [NavLink {:to ::frontpage} "Frontpage"]]
    [:li [NavLink {:to "/about"} "About"]]
    [:li [NavLink {:to ::item :path-params {:id 1}} "Item 1"]]
    [:li [NavLink {:to "/item/2?foo=bar"} "Item 2"]]
    [:li [NavLink {:to "/item/-1"} "Item -1 (redirects to frontpage)"]]
    [:li [NavLink {:to "http://www.google.fi"} "Google"]]]

   (if @current-match
     (let [view (:view (:data @current-match))]
       [view @current-match]))

   [:pre (with-out-str (fedn/pprint @current-match))]])

(defn init! []
  (rfe/start!
    router
    (fn [m] (reset! current-match m))
    ;; set to false to enable HistoryAPI
    {:use-fragment true})
  (r/render [current-page] (.getElementById js/document "app")))

(init!)

(comment
  (rf/match-by-path router "/about?kissa=1&koira=true")
  (rf/match-by-path router "/item/2?kissa=1&koira=true"))
