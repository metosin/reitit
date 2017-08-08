(ns reitit.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as reitit]))

(deftest reitit-test

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
      (is (= {:mw [:api], :parameters {:id String, :sub-id String}
              :route-params {:id "1", :sub-id "2"}}
             (reitit/match-route router "/api/user/1/2"))))))

