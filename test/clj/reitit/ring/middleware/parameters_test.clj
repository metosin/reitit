(ns reitit.ring.middleware.parameters-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring :as ring]))

(deftest parameters-test
  (let [app (ring/ring-handler
              (ring/router
                ["/ping" {:get #(select-keys % [:params :query-params])}]
                {:data {:middleware [parameters/parameters-middleware]}}))]
    (is (= {:query-params {"kikka" "kukka"}
            :params {"kikka" "kukka"}}
           (app {:request-method :get
                 :uri "/ping"
                 :query-string "kikka=kukka"})))))
