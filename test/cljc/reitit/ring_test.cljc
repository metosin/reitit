(ns reitit.ring-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as reitit]
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

  (testing "all paths should have a handler"
    (is (thrown-with-msg?
          ExceptionInfo
          #"^path '/ping' doesn't have a :handler defined$"
          (ring/router ["/ping"]))))

  (testing "ring-handler"
    (let [api-mw #(mw % :api)
          handler (fn handle
                    ([{:keys [::mw]}]
                     {:status 200 :body (conj mw :ok)})
                    ([request respond raise]
                      (respond (handle request))))
          app (ring/ring-handler
                (ring/router
                  [["/ping" handler]
                   ["/api" {:middleware [api-mw]}
                    ["/ping" handler]
                    ["/admin" {:middleware [[mw :admin]]}
                     ["/ping" handler]]]]))]

      (testing "normal handler"
        (is (= {:status 200, :body [:ok]}
               (app {:uri "/ping"}))))

      (testing "with middleware"
        (is (= {:status 200, :body [:api :ok :api]}
               (app {:uri "/api/ping"}))))

      (testing "with nested middleware"
        (is (= {:status 200, :body [:api :admin :ok :admin :api]}
               (app {:uri "/api/admin/ping"}))))

      (testing "not found"
        (is (= nil (app {:uri "/favicon.ico"}))))

      (testing "3-arity"
        (let [result (atom nil)
              respond (partial reset! result), raise ::not-called]
          (app {:uri "/api/admin/ping"} respond raise)
          (is (= {:status 200, :body [:api :admin :ok :admin :api]}
                 @result)))))))
