(ns example.server
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]))

(defn hello-world [request]
  (let [name (get-in request [:params :name] "World")]
    {:status 200 :body (str "Hello " name "!\n")}))

(defroutes routes
           [[["/"
              ["/hello" {:get hello-world}]]]])

(def service {:env                 :prod
              ::http/routes        routes
              ::http/resource-path "/public"
              ::http/type          :jetty
              ::http/port          8080})

(defn start []
  (-> service/service
      (merge {:env :dev
              ::http/join? false
              ::http/routes #(deref #'routes)
              ::http/allowed-origins {:creds true :allowed-origins (constantly true)}})
      http/default-interceptors
      http/dev-interceptors
      http/create-server
      http/start))


(comment
  (start))
