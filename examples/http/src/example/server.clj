(ns example.server
  (:require [reitit.http :as http]
            [reitit.ring :as ring]
            [clojure.core.async :as a]
            [reitit.interceptor.sieppari]
            [ring.adapter.jetty :as jetty]))

(defn -interceptor [f x]
  {:enter (fn [ctx] (f (update-in ctx [:request :via] (fnil conj []) x)))
   :leave (fn [ctx] (f (update-in ctx [:response :body] str "\n<- " x)))})

(def interceptor (partial -interceptor identity))
(def future-interceptor (partial -interceptor #(future %)))
(def async-interceptor (partial -interceptor #(a/go %)))

(defn -handler [f {:keys [via]}]
  (f {:status 200,
      :body (str (apply str (map #(str "-> " % "\n") via)) "   hello!")}))

(def handler (partial -handler identity))
(def future-handler (partial -handler #(future %)))
(def async-handler (partial -handler #(a/go %)))

(def app
  (http/ring-handler
    (http/router
      ["/api"
       {:interceptors [(interceptor :api)]}

       ["/sync"
        {:interceptors [(interceptor :sync)]
         :get {:interceptors [(interceptor :hello)]
               :handler handler}}]

       ["/future"
        {:interceptors [(future-interceptor :future)]
         :get {:interceptors [(future-interceptor :hello)]
               :handler future-handler}}]

       ["/async"
        {:interceptors [(async-interceptor :async)]
         :get {:interceptors [(async-interceptor :async-hello)]
               :handler async-handler}}]])
    (ring/create-default-handler)
    {:executor reitit.interceptor.sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false, :async? true})
  (println "server running in port 3000"))

(comment
  (start))
