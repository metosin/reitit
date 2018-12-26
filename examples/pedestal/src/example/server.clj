(ns example.server
  (:require [io.pedestal.http :as server]
            [clojure.core.async :as a]
            [reitit.pedestal :as pedestal]
            [muuntaja.interceptor]
            [reitit.http :as http]
            [reitit.ring :as ring]))

(defn interceptor [x]
  {:enter (fn [ctx] (update-in ctx [:request :via] (fnil conj []) {:enter x}))
   :leave (fn [ctx] (update-in ctx [:response :body] conj {:leave x}))})

(defn handler [{:keys [via]}]
  {:status 200,
   :body (conj via :handler)})

(def async-handler
  {:enter (fn [{:keys [request] :as ctx}]
            (a/go (assoc ctx :response (handler request))))})

(def router
  (pedestal/routing-interceptor
    (http/router
      ["/api"
       {:interceptors [(interceptor :api)]}

       ["/sync"
        {:interceptors [(interceptor :sync)]
         :get {:interceptors [(interceptor :get)]
               :handler handler}}]

       ["/async"
        {:interceptors [(interceptor :async)]
         :get {:interceptors [(interceptor :get) async-handler]}}]]

      ;; optional interceptors for all matched routes
      {:data {:interceptors [(interceptor :router)]}})

    ;; optional default ring handlers (if no routes have matched)
    (ring/routes
      (ring/create-resource-handler)
      (ring/create-default-handler))

    ;; optional top-level routes for both routes & default route
    {:interceptors [(muuntaja.interceptor/format-interceptor)
                    (interceptor :top)]}))

(defn start []
  (-> {::server/type :jetty
       ::server/port 3000
       ::server/join? false
       ;; no pedestal routes
       ::server/routes []}
      (server/default-interceptors)
      ;; use the reitit router
      (pedestal/replace-last-interceptor router)
      (server/dev-interceptors)
      (server/create-server)
      (server/start))
  (println "server running in port 3000"))

(comment
  (start))
