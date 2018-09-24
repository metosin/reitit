(ns reitit.http.interceptors.parameters-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http :as http]
            [reitit.interceptor.sieppari :as sieppari]))

(deftest parameters-test
  (let [app (http/ring-handler
              (http/router
                ["/ping" {:get #(select-keys % [:params :query-params])}]
                {:data {:interceptors [(parameters/parameters-interceptor)]}})
              {:executor sieppari/executor})]
    (is (= {:query-params {"kikka" "kukka"}
            :params {"kikka" "kukka"}}
           (app {:request-method :get
                 :uri "/ping"
                 :query-string "kikka=kukka"})))))
