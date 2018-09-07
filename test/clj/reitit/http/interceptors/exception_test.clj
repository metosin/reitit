(ns reitit.http.interceptors.exception-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.http.interceptors.exception :as exception]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.coercion.spec]
            [reitit.http.coercion]
            [muuntaja.core :as m])
  (:import (java.sql SQLException SQLWarning)))

(derive ::kikka ::kukka)

(deftest exception-test
  (letfn [(create
            ([f]
             (create f nil))
            ([f wrap]
             (http/ring-handler
               (http/router
                 [["/defaults"
                   {:handler f}]
                  ["/coercion"
                   {:interceptors [(reitit.http.coercion/coerce-request-interceptor)
                                   (reitit.http.coercion/coerce-response-interceptor)]
                    :coercion reitit.coercion.spec/coercion
                    :parameters {:query {:x int?, :y int?}}
                    :responses {200 {:body {:total pos-int?}}}
                    :handler f}]]
                 {:data {:interceptors [(exception/exception-interceptor
                                          (merge
                                            exception/default-handlers
                                            {::kikka (constantly {:status 400, :body "kikka"})
                                             SQLException (constantly {:status 400, :body "sql"})
                                             ::exception/wrap wrap}))]}})
               {:executor sieppari/executor})))]

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
        (is (= {:status 400, :body "kikka"}
               (app {:request-method :get, :uri "/defaults"})))))

    (testing "parent :type"
      (let [app (create (fn [_] (throw (ex-info "fail" {:type ::kukka}))))]
        (is (= {:status 400, :body "kikka"}
               (app {:request-method :get, :uri "/defaults"})))))

    (testing "exact Exception"
      (let [app (create (fn [_] (throw (SQLException.))))]
        (is (= {:status 400, :body "sql"}
               (app {:request-method :get, :uri "/defaults"})))))

    (testing "Exception SuperClass"
      (let [app (create (fn [_] (throw (SQLWarning.))))]
        (is (= {:status 400, :body "sql"}
               (app {:request-method :get, :uri "/defaults"})))))

    (testing "::exception/wrap"
      (let [calls (atom 0)
            app (create (fn [_] (throw (SQLWarning.)))
                        (fn [handler exception request]
                          (if (< (swap! calls inc) 2)
                            (handler exception request)
                            {:status 500, :body "too many tries"})))]
        (is (= {:status 400, :body "sql"}
               (app {:request-method :get, :uri "/defaults"})))
        (is (= {:status 500, :body "too many tries"}
               (app {:request-method :get, :uri "/defaults"})))))))
