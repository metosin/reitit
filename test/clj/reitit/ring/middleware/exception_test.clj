(ns reitit.ring.middleware.exception-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.coercion.spec]
            [reitit.ring.coercion]
            [muuntaja.core :as m])
  (:import (java.sql SQLException SQLWarning)))

(derive ::kikka ::kukka)

(deftest exception-test
  (letfn [(create [f]
            (ring/ring-handler
              (ring/router
                [["/defaults"
                  {:handler f}]
                 ["/coercion"
                  {:middleware [reitit.ring.coercion/coerce-request-middleware
                                reitit.ring.coercion/coerce-response-middleware]
                   :coercion reitit.coercion.spec/coercion
                   :parameters {:query {:x int?, :y int?}}
                   :responses {200 {:body {:total pos-int?}}}
                   :handler f}]]
                {:data {:middleware [(exception/create-exception-middleware
                                       (update
                                         exception/default-options :handlers merge
                                         {::kikka (constantly {:status 200, :body "kikka"})
                                          SQLException (constantly {:status 200, :body "sql"})}))]}})))]

    (testing "normal calls work ok"
      (let [response {:status 200, :body "ok"}
            app (create (fn [_] response))]
        (is (= response (app {:request-method :get, :uri "/defaults"})))))

    (testing "unknown exception"
      (let [app (create (fn [_] (throw (NullPointerException.))))]
        (is (= {:status 500
                :body {:type "exception"
                       :class "java.lang.NullPointerException"}}
               (app {:request-method :get, :uri "/defaults"}))))
      (let [app (create (fn [_] (throw (ex-info "fail" {:type ::invalid}))))]
        (is (= {:status 500
                :body {:type "exception"
                       :class "clojure.lang.ExceptionInfo"}}
               (app {:request-method :get, :uri "/defaults"})))))

    (testing "::ring/response"
      (let [response {:status 200, :body "ok"}
            app (create (fn [_] (throw (ex-info "fail" {:type ::ring/response, :response response}))))]
        (is (= response (app {:request-method :get, :uri "/defaults"})))))

    (testing ":muuntaja/decode"
      (let [app (create (fn [_] (m/decode m/instance "application/json" "{:so \"invalid\"}")))]
        (is (= {:body "Malformed \"application/json\" request."
                :headers {"Content-Type" "text/plain"}
                :status 400}
               (app {:request-method :get, :uri "/defaults"}))))

      (testing "::coercion/request-coercion"
        (let [app (create (fn [{{{:keys [x y]} :query} :parameters}]
                            {:status 200, :body {:total (+ x y)}}))]

          (let [{:keys [status body]} (app {:request-method :get
                                            :uri "/coercion"
                                            :query-params {"x" "1", "y" "2"}})]
            (is (= 200 status))
            (is (= {:total 3} body)))

          (let [{:keys [status body]} (app {:request-method :get
                                            :uri "/coercion"
                                            :query-params {"x" "abba", "y" "2"}})]
            (is (= 400 status))
            (is (= :reitit.coercion/request-coercion (:type body))))

          (let [{:keys [status body]} (app {:request-method :get
                                            :uri "/coercion"
                                            :query-params {"x" "-10", "y" "2"}})]
            (is (= 500 status))
            (is (= :reitit.coercion/response-coercion (:type body)))))))

    (testing "exact :type"
      (let [app (create (fn [_] (throw (ex-info "fail" {:type ::kikka}))))]
        (is (= {:status 200, :body "kikka"}
               (app {:request-method :get, :uri "/defaults"})))))

    (testing "parent :type"
      (let [app (create (fn [_] (throw (ex-info "fail" {:type ::kukka}))))]
        (is (= {:status 200, :body "kikka"}
               (app {:request-method :get, :uri "/defaults"})))))

    (testing "exact Exception"
      (let [app (create (fn [_] (throw (SQLException.))))]
        (is (= {:status 200, :body "sql"}
               (app {:request-method :get, :uri "/defaults"})))))

    (testing "Exception SuperClass"
      (let [app (create (fn [_] (throw (SQLWarning.))))]
        (is (= {:status 200, :body "sql"}
               (app {:request-method :get, :uri "/defaults"})))))))
