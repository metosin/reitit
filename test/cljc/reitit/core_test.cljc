(ns reitit.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as reitit]))

(def routes2
  ["/api" {:mw [:api]}
   ["/ping" :kikka]
   ["/user/:id" {:parameters {:id String}}
    ["/:sub-id" {:parameters {:sub-id String}}]]
   ["/pong"]
   ["/admin" {:mw [:admin] :roles #{:admin}}
    ["/user" {:roles #{:user}}]
    ["/db" {:mw [:db]}]]])

(deftest reitit-test
  (testing "bide sample"
    (let [routes [["/auth/login" :auth/login]
                  ["/auth/recovery/token/:token" :auth/recovery]
                  ["/workspace/:project-uuid/:page-uuid" :workspace/page]]]
	    (is (= [["/auth/login" {:handler :auth/login}]
	            ["/auth/recovery/token/:token" {:handler :auth/recovery}]
	            ["/workspace/:project-uuid/:page-uuid" {:handler :workspace/page}]]
	           (reitit/resolve-routes routes)))))
  (testing "ring sample"
    (let [routes ["/api" {:mw [:api]}
				  ["/ping" :kikka]
				  ["/user/:id" {:parameters {:id String}}
				   ["/:sub-id" {:parameters {:sub-id String}}]]
				  ["/pong"]
				  ["/admin" {:mw [:admin] :roles #{:admin}}
				   ["/user" {:roles #{:user}}]
				   ["/db" {:mw [:db]}]]]]
      (is (= [["/api/ping" {:mw [:api], :handler :kikka}]
              ["/api/user/:id/:sub-id" {:mw [:api], :parameters {:id String, :sub-id String}}]
              ["/api/pong" {:mw [:api]}]
              ["/api/admin/user" {:mw [:api :admin], :roles #{:user}}]
              ["/api/admin/db" {:mw [:api :admin :db], :roles #{:admin}}]]
              (reitit/resolve-routes {:mw :into} routes))))))