(ns reitit.ring.middleware.exception-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [muuntaja.core :as m]
            [reitit.coercion :as coercion]
            [reitit.coercion.spec]
            [reitit.ring :as ring]
            [reitit.ring.coercion]
            [reitit.ring.middleware.exception :as exception]
            [ring.util.http-response :as http-response])
  (:import (clojure.lang ExceptionInfo)
           (java.sql SQLException SQLWarning)))

(derive ::kikka ::kukka)

(deftest exception-test
  (letfn [(create
            ([f]
             (create f nil))
            ([f wrap]
             (ring/ring-handler
              (ring/router
               [["/defaults"
                 {:handler f}]
                ["/http-response"
                 {:handler (fn [req]
                             (http-response/unauthorized! "Unauthorized"))}]
                ["/coercion"
                 {:middleware [reitit.ring.coercion/coerce-request-middleware
                               reitit.ring.coercion/coerce-response-middleware]
                  :coercion reitit.coercion.spec/coercion
                  :parameters {:query {:x int?, :y int?}}
                  :responses {200 {:body {:total pos-int?}}}
                  :handler f}]]
               {:data {:middleware [(exception/create-exception-middleware
                                     (merge
                                      exception/default-handlers
                                      {::kikka (constantly (http-response/bad-request "kikka"))
                                       SQLException (constantly (http-response/bad-request "sql"))
                                       ::exception/wrap wrap}))]}}))))]

    (testing "normal calls work ok"
      (let [response {:status 200, :body "ok"}
            app (create (fn [_] response))]
        (is (= response (app {:request-method :get, :uri "/defaults"})))))

    (testing "unknown exception"
      (let [app (create (fn [_] (throw (NullPointerException.))))
            {:keys [status body] :as resp} (app {:request-method :get, :uri "/defaults"})]
        (is (http-response/response? resp))
        (is (= status 500))
        (is (= body {:type "exception"
                     :class "java.lang.NullPointerException"})))
      (let [app (create (fn [_] (throw (ex-info "fail" {:type ::invalid}))))
            {:keys [status body] :as resp} (app {:request-method :get, :uri "/defaults"})]
        (is (http-response/response? resp))
        (is (= status 500))
        (is (= body {:type "exception"
                     :class "clojure.lang.ExceptionInfo"}))))

    (testing "::ring/response"
      (let [response (http-response/ok "ok")
            app (create (fn [_] (throw (ex-info "fail" {:type ::ring/response, :response response}))))]
        (is (= response (app {:request-method :get, :uri "/defaults"})))))

    (testing "::ring.util.http-response/response"
      (let [response {:status 401 :body "Unauthorized" :headers {}}
            app (create (fn [_] (throw (ex-info "Unauthorized!" {:type ::http-response/response
                                                                 :response response}))))]
        (is (= response (app {:request-method :post, :uri "/http-response"})))))

    (testing ":muuntaja/decode"
      (let [app (create (fn [_] (m/decode m/instance "application/json" "{:so \"invalid\"}")))]
        (is (= {:body "Malformed \"application/json\" request."
                :headers {"Content-Type" "text/plain"}
                :status 400}
               (app {:request-method :get, :uri "/defaults"}))))

      (testing "::coercion/request-coercion"
        (let [app (create (fn [{{{:keys [x y]} :query} :parameters}]
                            (http-response/ok {:total (+ x y)})))]

          (let [{:keys [status body] :as resp} (app {:request-method :get
                                                     :uri            "/coercion"
                                                     :query-params   {"x" "1", "y" "2"}})]
            (is (http-response/response? resp))
            (is (= 200 status))
            (is (= {:total 3} body)))

          (let [{:keys [status body] :as resp} (app {:request-method :get
                                                     :uri            "/coercion"
                                                     :query-params   {"x" "abba", "y" "2"}})]
            (is (http-response/response? resp))
            (is (= 400 status))
            (is (= :reitit.coercion/request-coercion (:type body))))

          (let [{:keys [status body] :as resp} (app {:request-method :get
                                                     :uri            "/coercion"
                                                     :query-params   {"x" "-10", "y" "2"}})]
            (is (http-response/response? resp))
            (is (= 500 status))
            (is (= :reitit.coercion/response-coercion (:type body)))))))

    (testing "exact :type"
      (let [app (create (fn [_] (throw (ex-info "fail" {:type ::kikka}))))
            {:keys [status body] :as resp} (app {:request-method :get, :uri "/defaults"})]
        (is (http-response/response? resp))
        (is (= status 400))
        (is (= body "kikka"))))

    (testing "parent :type"
      (let [app (create (fn [_] (throw (ex-info "fail" {:type ::kukka}))))
            {:keys [status body] :as resp} (app {:request-method :get, :uri "/defaults"})]
        (is (http-response/response? resp))
        (is (= status 400))
        (is (= body "kikka"))))

    (testing "exact Exception"
      (let [app (create (fn [_] (throw (SQLException.))))
            {:keys [status body] :as resp} (app {:request-method :get, :uri "/defaults"})]
        (is (http-response/response? resp))
        (is (= status 400))
        (is (= body "sql"))))

    (testing "Exception SuperClass"
      (let [app (create (fn [_] (throw (SQLWarning.))))
            {:keys [status body] :as resp} (app {:request-method :get, :uri "/defaults"})]
        (is (http-response/response? resp))
        (is (= status 400))
        (is (= body "sql"))))

    (testing "::exception/wrap"
      (let [calls (atom 0)
            app (create (fn [_] (throw (SQLWarning.)))
                        (fn [handler exception request]
                          (if (< (swap! calls inc) 2)
                            (handler exception request)
                            {:status 500
                             :headers {}
                             :body "too many tries"})))]
        (let [{:keys [status body] :as resp} (app {:request-method :get, :uri "/defaults"})]
          (is (http-response/response? resp))
          (is (= status 400))
          (is (= body "sql")))
        (let [{:keys [status body] :as resp} (app {:request-method :get, :uri "/defaults"})]
          (is (http-response/response? resp))
          (is (= status 500))
          (is (= body "too many tries")))))))

(deftest spec-coercion-exception-test
  (let [app (ring/ring-handler
             (ring/router
              ["/plus"
               {:get
                {:parameters {:query {:x int?, :y int?}}
                 :responses {200 {:body {:total pos-int?}}}
                 :handler (fn [{{{:keys [x y]} :query} :parameters}]
                            (http-response/ok {:total (+ x y)}))}}]
              {:data {:coercion reitit.coercion.spec/coercion
                      :middleware [(exception/create-exception-middleware
                                    (merge
                                     exception/default-handlers
                                     {::coercion/request-coercion (fn [e _] (http-response/bad-request (ex-data e)) )
                                      ::coercion/response-coercion (fn [e _] {:status 500
                                                                              :headers {}
                                                                              :body (ex-data e)})}))
                                   reitit.ring.coercion/coerce-request-middleware
                                   reitit.ring.coercion/coerce-response-middleware]}}))]
    (testing "success"
      (let [{:keys [status body] :as resp} (app {:uri "/plus", :request-method :get, :query-params {"x" "1", "y" "2"}})]
        (is (http-response/response? resp))
        (is (= 200 status))
        (is (= body {:total 3}))))

    (testing "request error"
      (let [{:keys [status body] :as resp} (app {:uri "/plus", :request-method :get, :query-params {"x" "1", "y" "fail"}})]
        (is (http-response/response? resp))
        (is (= 400 status))
        (testing "spec error is exposed as is"
          (let [problems (:problems body)]
            (is (contains? problems ::s/spec))
            (is (contains? problems ::s/value))
            (is (contains? problems ::s/problems))))))

    (testing "response error"
      (let [{:keys [status body] :as resp} (app {:uri "/plus", :request-method :get, :query-params {"x" "1", "y" "-2"}})]
        (is (http-response/response? resp))
        (is (= 500 status))
        (testing "spec error is exposed as is"
          (let [problems (:problems body)]
            (is (contains? problems ::s/spec))
            (is (contains? problems ::s/value))
            (is (contains? problems ::s/problems))))))))

(deftest response-keys-test
  (is (thrown-with-msg?
       ExceptionInfo
       #"Response status must be int"
       (ring/ring-handler
        (ring/router
         [["/coercion"
           {:middleware [reitit.ring.coercion/coerce-response-middleware]
            :coercion reitit.coercion.spec/coercion
            :responses {:200 {:body {:total pos-int?}}}
            :handler identity}]])))))
