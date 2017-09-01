(ns reitit.middleware-test
  (:require [clojure.test :refer [deftest testing is]]
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
  (testing "middleware generators"
    (let [calls (atom 0)]

      (testing "record generator"
        (reset! calls 0)
        (let [syntax [(middleware/gen
                        (fn [meta _]
                          (swap! calls inc)
                          (fn [handler value]
                            (swap! calls inc)
                            (fn [request]
                              [meta value request]))))]
              app ((middleware/compose-middleware syntax :meta {}) identity :value)]
          (dotimes [_ 10]
            (is (= [:meta :value :request] (app :request)))
            (is (= 2 @calls)))))

      (testing "middleware generator as function"
        (reset! calls 0)
        (let [syntax (middleware/gen
                       (fn [meta _]
                         (swap! calls inc)
                         (fn [handler value]
                           (swap! calls inc)
                           (fn [request]
                             [meta value request]))))
              app ((syntax :meta nil) identity :value)]
          (dotimes [_ 10]
            (is (= [:meta :value :request] (app :request)))
            (is (= 2 @calls)))))

      (testing "generator vector"
        (reset! calls 0)
        (let [syntax [[(middleware/gen
                         (fn [meta _]
                           (swap! calls inc)
                           (fn [handler value]
                             (swap! calls inc)
                             (fn [request]
                               [meta value request])))) :value]]
              app ((middleware/compose-middleware syntax :meta {}) identity)]
          (is (= [:meta :value :request] (app :request)))
          (dotimes [_ 10]
            (is (= [:meta :value :request] (app :request)))
            (is (= 2 @calls)))))

      (testing "generator can return nil"
        (reset! calls 0)
        (let [syntax [[(middleware/gen
                         (fn [meta _])) :value]]
              app ((middleware/compose-middleware syntax :meta {}) identity)]
          (is (= :request (app :request)))
          (dotimes [_ 10]
            (is (= :request (app :request)))))))))


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
