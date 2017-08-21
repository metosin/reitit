(ns reitit.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as reitit #?@(:cljs [:refer [Match]])])
  #?(:clj
     (:import (reitit.core Match)
              (clojure.lang ExceptionInfo))))

(deftest reitit-test

  (testing "linear router"
    (let [router (reitit/router ["/api" ["/ipa" ["/:size" ::beer]]])]
      (is (= :linear-router (reitit/router-type router)))
      (is (= [["/api/ipa/:size" {:name ::beer}]]
             (reitit/routes router)))
      (is (= true (map? (reitit/options router))))
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
      (is (= nil (reitit/match-by-name router "ILLEGAL")))
      (is (= [::beer] (reitit/route-names router)))
      (testing "name-based routing with missing parameters"
        (is (= (reitit/map->PartialMatch
                 {:template "/api/ipa/:size"
                  :meta {:name ::beer}
                  :required #{:size}
                  :params nil})
               (reitit/match-by-name router ::beer)))
        (is (= true (reitit/partial-match? (reitit/match-by-name router ::beer))))
        (is (thrown-with-msg?
              ExceptionInfo
              #"^missing path-params for route /api/ipa/:size: \#\{:size\}$"
              (reitit/match-by-name! router ::beer))))))

  (testing "lookup router"
    (let [router (reitit/router ["/api" ["/ipa" ["/large" ::beer]]])]
      (is (= :lookup-router (reitit/router-type router)))
      (is (= [["/api/ipa/large" {:name ::beer}]]
             (reitit/routes router)))
      (is (= true (map? (reitit/options router))))
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
      (is (= nil (reitit/match-by-name router "ILLEGAL")))
      (is (= [::beer] (reitit/route-names router)))
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
                  ["/user/:id" {:parameters {:id "String"}}
                   ["/:sub-id" {:parameters {:sub-id "String"}}]]
                  ["/pong" pong]
                  ["/admin" {:mw [:admin] :roles #{:admin}}
                   ["/user" {:roles ^:replace #{:user}}]
                   ["/db" {:mw [:db]}]]]
          expected [["/api/ping" {:mw [:api], :name :kikka}]
                    ["/api/user/:id/:sub-id" {:mw [:api], :parameters {:id "String", :sub-id "String"}}]
                    ["/api/pong" {:mw [:api], :handler pong}]
                    ["/api/admin/user" {:mw [:api :admin], :roles #{:user}}]
                    ["/api/admin/db" {:mw [:api :admin :db], :roles #{:admin}}]]
          router (reitit/router routes)]
      (is (= expected (reitit/resolve-routes routes {})))
      (is (= (reitit/map->Match
               {:template "/api/user/:id/:sub-id"
                :meta {:mw [:api], :parameters {:id "String", :sub-id "String"}}
                :path "/api/user/1/2"
                :params {:id "1", :sub-id "2"}})
             (reitit/match-by-path router "/api/user/1/2"))))))

(deftest conflicting-routes-test
  (are [conflicting? data]
    (let [routes (reitit/resolve-routes data {})
          conflicts (-> routes (reitit/resolve-routes {}) (reitit/conflicting-routes))]
      (if conflicting? (seq conflicts) (nil? conflicts)))

    true [["/a"]
          ["/a"]]

    true [["/a"]
          ["/:b"]]

    true [["/a"]
          ["/*b"]]

    true [["/a/1/2"]
          ["/*b"]]

    false [["/a"]
           ["/a/"]]

    false [["/a"]
           ["/a/1"]]

    false [["/a"]
           ["/a/:b"]]

    false [["/a"]
           ["/a/*b"]]

    true [["/v2/public/messages/dataset/bulk"]
          ["/v2/public/messages/dataset/:dataset-id"]])

  (testing "all conflicts are returned"
    (is (= {["/a" {}] #{["/*d" {}] ["/:b" {}]},
            ["/:b" {}] #{["/c" {}] ["/*d" {}]},
            ["/c" {}] #{["/*d" {}]}}))
    (-> [["/a"] ["/:b"] ["/c"] ["/*d"]]
        (reitit/resolve-routes {})
        (reitit/conflicting-routes)))

  (testing "router with conflicting routes"
    (testing "throws by default"
      (is (thrown-with-msg?
            ExceptionInfo
            #"router contains conflicting routes"
            (reitit/router
              [["/a"] ["/a"]]))))
    (testing "can be configured to ignore"
      (is (not
            (nil?
              (reitit/router
                [["/a"] ["/a"]]
                {:conflicts (constantly nil)})))))))
