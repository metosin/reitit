(ns reitit.middleware-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.middleware :as middleware]
            [clojure.set :as set]
            [reitit.core :as reitit])
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

(deftest expand-middleware-test

  (testing "middleware records"

    (testing ":name is mandatory"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Middleware must have :name defined"
            (middleware/create
              {:wrap identity
               :gen (constantly identity)}))))

    (testing ":wrap & :gen are exclusive"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Middleware can't both :wrap and :gen defined"
            (middleware/create
              {:name ::test
               :wrap identity
               :gen (constantly identity)}))))

    (testing ":wrap"
      (let [calls (atom 0)
            data {:name ::test
                  :wrap (fn [handler value]
                          (swap! calls inc)
                          (fn [request]
                            [value request]))}]

        (testing "as map"
          (reset! calls 0)
          (let [app ((middleware/compose-middleware [data] :meta {}) identity :value)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))

        (testing "direct"
          (reset! calls 0)
          (let [app ((middleware/compose-middleware [(middleware/create data)] :meta {}) identity :value)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))

        (testing "vector"
          (reset! calls 0)
          (let [app ((middleware/compose-middleware [[(middleware/create data) :value]] :meta {}) identity)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))))

    (testing ":gen"
      (let [calls (atom 0)
            data {:name ::test
                  :gen (fn [meta _]
                         (swap! calls inc)
                         (fn [handler value]
                           (swap! calls inc)
                           (fn [request]
                             [meta value request])))}]

        (testing "as map"
          (reset! calls 0)
          (let [app ((middleware/compose-middleware [data] :meta {}) identity :value)]
            (dotimes [_ 10]
              (is (= [:meta :value :request] (app :request)))
              (is (= 2 @calls)))))

        (testing "direct"
          (reset! calls 0)
          (let [app ((middleware/compose-middleware [(middleware/create data)] :meta {}) identity :value)]
            (dotimes [_ 10]
              (is (= [:meta :value :request] (app :request)))
              (is (= 2 @calls)))))

        (testing "vector"
          (reset! calls 0)
          (let [app ((middleware/compose-middleware [[(middleware/create data) :value]] :meta {}) identity)]
            (is (= [:meta :value :request] (app :request)))
            (dotimes [_ 10]
              (is (= [:meta :value :request] (app :request)))
              (is (= 2 @calls)))))

        (testing "nil unmounts the middleware"
          (reset! calls 0)
          (let [syntax [[(middleware/create
                           {:name ::test
                            :gen (fn [meta _])}) :value]]
                app ((middleware/compose-middleware syntax :meta {}) identity)]
            (is (= :request (app :request)))
            (dotimes [_ 10]
              (is (= :request (app :request))))))))))

(deftest middleware-router-test

  (testing "all paths should have a handler"
    (is (thrown-with-msg?
          ExceptionInfo
          #"path \"/ping\" doesn't have a :handler defined"
          (middleware/router ["/ping"]))))

  (testing "ring-handler"
    (let [api-mw #(mw % :api)
          router (middleware/router
                   [["/ping" handler]
                    ["/api" {:middleware [api-mw]}
                     ["/ping" handler]
                     ["/admin" {:middleware [[mw :admin]]}
                      ["/ping" handler]]]])
          app (fn
                ([{:keys [uri] :as request}]
                 (if-let [handler (:result (reitit/match-by-path router uri))]
                   (handler request)))
                ([{:keys [uri] :as request} respond raise]
                 (if-let [handler (:result (reitit/match-by-path router uri))]
                   (handler request respond raise))))]

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
