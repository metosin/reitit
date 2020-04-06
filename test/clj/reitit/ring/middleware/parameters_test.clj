(ns reitit.ring.middleware.parameters-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring :as ring])
  (:import (java.io ByteArrayInputStream)))

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

(deftest form-parameters-test
  (testing "simple case"
    (let [app (ring/ring-handler
               (ring/router
                ["/ping" {:get #(select-keys % [:params :form-params])}]
                {:data {:middleware [parameters/form-parameters-middleware]}}))]
      (is (= {:form-params {"kikka" "kukka"}
              :params      {"kikka" "kukka"}}
             (app {:request-method :get
                   :headers        {"content-type" "application/x-www-form-urlencoded"}
                   :uri            "/ping"
                   :body           (ByteArrayInputStream. (.getBytes "kikka=kukka"))})))))

  (testing "merges with existing params"
    (let [app (ring/ring-handler
               (ring/router
                ["/ping" {:get #(select-keys % [:params :form-params])}]
                {:data {:middleware [parameters/form-parameters-middleware]}}))]
      (is (= {:form-params {"kikka" "kukka"}
              :params      {"kikka" "kukka"
                            "kakka" "kekka"}}
             (app {:request-method :get
                   :headers        {"content-type" "application/x-www-form-urlencoded"}
                   :uri            "/ping"
                   :params         {"kakka" "kekka"}
                   :body           (ByteArrayInputStream. (.getBytes "kikka=kukka"))})))))

  (testing "no-op if `form-params` is already present"
    (let [app (ring/ring-handler
               (ring/router
                ["/ping" {:get #(select-keys % [:params :form-params])}]
                {:data {:middleware [parameters/form-parameters-middleware]}}))]
      (is (= {:form-params {}}
             (app {:request-method :get
                   :headers        {"content-type" "application/x-www-form-urlencoded"}
                   :uri            "/ping"
                   :form-params    {}
                   :body           (ByteArrayInputStream. (.getBytes "kikka=kukka"))}))))))

(deftest query-parameters-test
  (testing "simple case"
    (let [app (ring/ring-handler
               (ring/router
                ["/ping" {:get #(select-keys % [:params :query-params])}]
                {:data {:middleware [parameters/query-parameters-middleware]}}))]
      (is (= {:query-params {"kikka" "kukka"}
              :params      {"kikka" "kukka"}}
             (app {:request-method :get
                   :headers        {"content-type" "application/x-www-form-urlencoded"}
                   :uri            "/ping"
                   :query-string   "kikka=kukka"})))))

  (testing "merges with existing params"
    (let [app (ring/ring-handler
               (ring/router
                ["/ping" {:get #(select-keys % [:params :query-params])}]
                {:data {:middleware [parameters/query-parameters-middleware]}}))]
      (is (= {:query-params {"kikka" "kukka"}
              :params       {"kikka" "kukka"
                             "kakka" "kekka"}}
             (app {:request-method :get
                   :headers        {"content-type" "application/x-www-form-urlencoded"}
                   :uri            "/ping"
                   :params         {"kakka" "kekka"}
                   :query-string   "kikka=kukka"})))))

  (testing "no-op if `query-params` is already present"
    (let [app (ring/ring-handler
               (ring/router
                ["/ping" {:get #(select-keys % [:params :query-params])}]
                {:data {:middleware [parameters/query-parameters-middleware]}}))]
      (is (= {:query-params {}}
             (app {:request-method :get
                   :headers        {"content-type" "application/x-www-form-urlencoded"}
                   :uri            "/ping"
                   :query-params   {}
                   :query-string   "kikka=kukka"}))))))
