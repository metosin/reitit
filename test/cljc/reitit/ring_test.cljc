(ns reitit.ring-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defn mw [handler name]
  (fn
    ([request]
     (-> request
         (update ::mw (fnil conj []) name)
         (handler)
         (update :body (fnil conj []) name)))
    ([request respond raise]
     (handler
       (update request ::mw (fnil conj []) name)
       #(respond (update % :body (fnil conj []) name))
       raise))))

(deftest ring-test

  (testing "simple-router"

    (testing "all paths should have a handler"
      (is (thrown-with-msg?
            ExceptionInfo
            #"path \"/ping\" doesn't have a :handler defined"
            (ring/simple-router ["/ping"]))))

    (testing "ring-handler"
      (let [api-mw #(mw % :api)
            handler (fn handle
                      ([{:keys [::mw]}]
                       {:status 200 :body (conj mw :ok)})
                      ([request respond raise]
                       (respond (handle request))))
            router (ring/simple-router
                     [["/ping" handler]
                      ["/api" {:middleware [api-mw]}
                       ["/ping" handler]
                       ["/admin" {:middleware [[mw :admin]]}
                        ["/ping" handler]]]])
            app (ring/ring-handler router)]

        (testing "router can be extracted"
          (is (= router (ring/get-router app))))

        (testing "not found"
          (is (= nil (app {:uri "/favicon.ico"}))))

        (testing "normal handler"
          (is (= {:status 200, :body [:ok]}
                 (app {:uri "/ping"}))))

        (testing "with middleware"
          (is (= {:status 200, :body [:api :ok :api]}
                 (app {:uri "/api/ping"}))))

        (testing "with nested middleware"
          (is (= {:status 200, :body [:api :admin :ok :admin :api]}
                 (app {:uri "/api/admin/ping"}))))

        (testing "3-arity"
          (let [result (atom nil)
                respond (partial reset! result), raise ::not-called]
            (app {:uri "/api/admin/ping"} respond raise)
            (is (= {:status 200, :body [:api :admin :ok :admin :api]}
                   @result)))))))

  (testing "method-router"

    (testing "all paths should have a handler"
      (is (thrown-with-msg?
            ExceptionInfo
            #"path \"/ping\" doesn't have a :handler defined for method :get"
            (ring/method-router ["/ping" {:get {}}]))))

    (testing "ring-handler"
      (let [api-mw #(mw % :api)
            handler (fn handle
                      ([{:keys [::mw]}]
                       {:status 200 :body (conj mw :ok)})
                      ([request respond raise]
                       (respond (handle request))))
            router (ring/method-router
                     [["/api" {:middleware [api-mw]}
                       ["/all" handler]
                       ["/get" {:get handler}]
                       ["/users" {:middleware [[mw :users]]
                                  :get handler
                                  :post {:handler handler
                                         :middleware [[mw :post]]}
                                  :handler handler}]]])
            app (ring/ring-handler router)]

        (testing "router can be extracted"
          (is (= router (ring/get-router app))))

        (testing "not found"
          (is (= nil (app {:uri "/favicon.ico"}))))

        (testing "catch all handler"
          (is (= {:status 200, :body [:api :ok :api]}
                 (app {:uri "/api/all" :request-method :get}))))

        (testing "just get handler"
          (is (= {:status 200, :body [:api :ok :api]}
                 (app {:uri "/api/get" :request-method :get})))
          (is (= nil (app {:uri "/api/get" :request-method :post}))))

        (testing "expanded method handler"
          (is (= {:status 200, :body [:api :users :ok :users :api]}
                 (app {:uri "/api/users" :request-method :get}))))

        (testing "method handler with middleware"
          (is (= {:status 200, :body [:api :users :post :ok :post :users :api]}
                 (app {:uri "/api/users" :request-method :post}))))

        (testing "fallback handler"
          (is (= {:status 200, :body [:api :users :ok :users :api]}
                 (app {:uri "/api/users" :request-method :put}))))

        (testing "3-arity"
          (let [result (atom nil)
                respond (partial reset! result), raise ::not-called]
            (app {:uri "/api/users" :request-method :post} respond raise)
            (is (= {:status 200, :body [:api :users :post :ok :post :users :api]}
                   @result))))))))
