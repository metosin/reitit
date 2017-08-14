(ns reitit.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.core :as reitit #?@(:cljs [:refer [Match LinearRouter LookupRouter]])])
  #?(:clj
     (:import (reitit.core Match LinearRouter LookupRouter)
              (clojure.lang ExceptionInfo))))

(deftest reitit-test

  (testing "linear router"
    (let [router (reitit/router ["/api" ["/ipa" ["/:size" ::beer]]])]
      (is (instance? LinearRouter router))
      (is (= [["/api/ipa/:size" {:name ::beer}]]
             (reitit/routes router)))
      (is (= (reitit/map->Match
               {:template "/api/ipa/:size"
                :meta {:name ::beer}
                :path "/api/ipa/large"
                :params {:size "large"}})
             (reitit/match-by-path router "/api/ipa/large")))
      (is (= (reitit/map->Match
               {:template "/api/ipa/:size"
                :meta {:name ::beer}
                :path "/api/ipa/large"
                :params {:size "large"}})
             (reitit/match-by-name router ::beer {:size "large"})))
      (testing "name-based routing at runtime for missing parameters"
        (is (thrown-with-msg?
              ExceptionInfo
              #"^missing path-params for route '/api/ipa/:size': \#\{:size\}$"
              (reitit/match-by-name router ::beer))))))

  (testing "lookup router"
    (let [router (reitit/router ["/api" ["/ipa" ["/large" ::beer]]])]
      (is (instance? LookupRouter router))
      (is (= [["/api/ipa/large" {:name ::beer}]]
             (reitit/routes router)))
      (is (= (reitit/map->Match
               {:template "/api/ipa/large"
                :meta {:name ::beer}
                :path "/api/ipa/large"
                :params {}})
             (reitit/match-by-path router "/api/ipa/large")))
      (is (= (reitit/map->Match
               {:template "/api/ipa/large"
                :meta {:name ::beer}
                :path "/api/ipa/large"
                :params {:size "large"}})
             (reitit/match-by-name router ::beer {:size "large"})))
      (testing "can't be created with wildcard routes"
        (is (thrown-with-msg?
              ExceptionInfo
              #"can't create LookupRouter with wildcard routes"
              (reitit/lookup-router
                (reitit/resolve-routes
                  ["/api/:version/ping"] {})))))))

  (testing "route coercion & compilation"
    (testing "custom compile"
      (let [compile-times (atom 0)
            coerce (fn [[path meta] _]
                     (if-not (:invalid? meta)
                       [path (assoc meta :path path)]))
            compile (fn [[path meta] _]
                      (swap! compile-times inc)
                      (constantly path))
            router (reitit/router
                     ["/api" {:roles #{:admin}}
                      ["/ping" ::ping]
                      ["/pong" ::pong]
                      ["/hidden" {:invalid? true}
                       ["/utter"]
                       ["/crap"]]]
                     {:coerce coerce
                      :compile compile})]
        (testing "routes are coerced"
          (is (= [["/api/ping" {:name ::ping
                                :path "/api/ping",
                                :roles #{:admin}}]
                  ["/api/pong" {:name ::pong
                                :path "/api/pong",
                                :roles #{:admin}}]]
                 (reitit/routes router))))
        (testing "route match contains compiled handler"
          (is (= 2 @compile-times))
          (let [{:keys [handler]} (reitit/match-by-path router "/api/pong")]
            (is handler)
            (is (= "/api/pong" (handler)))
            (is (= 2 @compile-times))))))
    (testing "default compile"
      (let [router (reitit/router ["/ping" (constantly "ok")])]
        (let [{:keys [handler]} (reitit/match-by-path router "/ping")]
          (is handler)
          (is (= "ok" (handler)))))))

  (testing "bide sample"
    (let [routes [["/auth/login" :auth/login]
                  ["/auth/recovery/token/:token" :auth/recovery]
                  ["/workspace/:project-uuid/:page-uuid" :workspace/page]]
          expected [["/auth/login" {:name :auth/login}]
                    ["/auth/recovery/token/:token" {:name :auth/recovery}]
                    ["/workspace/:project-uuid/:page-uuid" {:name :workspace/page}]]]
      (is (= expected (reitit/resolve-routes routes {})))))

  (testing "ring sample"
    (let [pong (constantly "ok")
          routes ["/api" {:mw [:api]}
                  ["/ping" :kikka]
                  ["/user/:id" {:parameters {:id String}}
                   ["/:sub-id" {:parameters {:sub-id String}}]]
                  ["/pong" pong]
                  ["/admin" {:mw [:admin] :roles #{:admin}}
                   ["/user" {:roles ^:replace #{:user}}]
                   ["/db" {:mw [:db]}]]]
          expected [["/api/ping" {:mw [:api], :name :kikka}]
                    ["/api/user/:id/:sub-id" {:mw [:api], :parameters {:id String, :sub-id String}}]
                    ["/api/pong" {:mw [:api], :handler pong}]
                    ["/api/admin/user" {:mw [:api :admin], :roles #{:user}}]
                    ["/api/admin/db" {:mw [:api :admin :db], :roles #{:admin}}]]
          router (reitit/router routes)]
      (is (= expected (reitit/resolve-routes routes {})))
      (is (= (reitit/map->Match
               {:template "/api/user/:id/:sub-id"
                :meta {:mw [:api], :parameters {:id String, :sub-id String}}
                :path "/api/user/1/2"
                :params {:id "1", :sub-id "2"}})
             (reitit/match-by-path router "/api/user/1/2"))))))
