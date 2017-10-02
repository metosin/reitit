(ns reitit.ring-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.middleware :as middleware]
            [reitit.ring :as ring]
            [clojure.set :as set]
            [reitit.core :as r])
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

(defn handler
  ([{:keys [::mw]}]
   {:status 200 :body (conj mw :ok)})
  ([request respond raise]
   (respond (handler request))))

(deftest ring-router-test

  (testing "all paths should have a handler"
    (is (thrown-with-msg?
          ExceptionInfo
          #"path \"/ping\" doesn't have a :handler defined for :get"
          (ring/router ["/ping" {:get {}}]))))

  (testing "ring-handler"
    (let [api-mw #(mw % :api)
          router (ring/router
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
                 @result))))))

  (testing "named routes"
    (let [router (ring/router
                   [["/api"
                     ["/all" {:handler handler :name ::all}]
                     ["/get" {:get {:handler handler :name ::HIDDEN}
                              :name ::get}]
                     ["/users" {:get handler
                                :post handler
                                :handler handler
                                :name ::users}]]])
          app (ring/ring-handler router)]

      (testing "router can be extracted"
        (is (= router (ring/get-router app))))

      (testing "only top-level route names are matched"
        (is (= [::all ::get ::users]
               (r/route-names router))))

      (testing "all named routes can be matched"
        (doseq [name (r/route-names router)]
          (is (= name (-> (r/match-by-name router name) :meta :name))))))))

(defn wrap-enforce-roles [handler]
  (fn [{:keys [::roles] :as request}]
    (let [required (some-> request (ring/get-match) :meta ::roles)]
      (if (and (seq required) (not (set/intersection required roles)))
        {:status 403, :body "forbidden"}
        (handler request)))))

(deftest enforcing-meta-data-rules-at-runtime-test
  (let [handler (constantly {:status 200, :body "ok"})
        app (ring/ring-handler
              (ring/router
                [["/api"
                  ["/ping" handler]
                  ["/admin" {::roles #{:admin}}
                   ["/ping" handler]]]]
                {:meta {:middleware [wrap-enforce-roles]}}))]

    (testing "public handler"
      (is (= {:status 200, :body "ok"}
             (app {:uri "/api/ping" :request-method :get}))))

    (testing "runtime-enforced handler"
      (testing "without needed roles"
        (is (= {:status 403 :body "forbidden"}
               (app {:uri "/api/admin/ping"
                     :request-method :get}))))
      (testing "with needed roles"
        (is (= {:status 200, :body "ok"}
               (app {:uri "/api/admin/ping"
                     :request-method :get
                     ::roles #{:admin}})))))))
