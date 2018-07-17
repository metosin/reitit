(ns reitit.ring.middleware.muuntaja-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.ring.middleware.alpha.muuntaja :as muuntaja]
            [muuntaja.core :as m]))

(deftest muuntaja-test
  (let [data {:kikka "kukka"}
        app (ring/ring-handler
              (ring/router
                ["/ping" {:get (constantly {:status 200, :body data})}]
                {:data {:middleware [(muuntaja/create-format-middleware)]}}))]
    (is (= data (->> {:request-method :get, :uri "/ping"}
                     (app)
                     :body
                     (m/decode m/instance "application/json"))))))

;; TODO: test swagger!
