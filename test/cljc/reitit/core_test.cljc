(ns reitit.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r #?@(:cljs [:refer [Match Router]])])
  #?(:clj
     (:import (reitit.core Match Router)
              (clojure.lang ExceptionInfo))))

(deftest reitit-test

  (testing "routers handling wildcard paths"
    (are [r name]
      (testing "wild"

        (testing "simple"
          (let [router (r/router ["/api" ["/ipa" ["/:size" ::beer]]] {:router r})]
            (is (= name (r/router-name router)))
            (is (= [["/api/ipa/:size" {:name ::beer} nil]]
                   (r/routes router)))
            (is (map? (r/options router)))
            (is (= (r/map->Match
                     {:template "/api/ipa/:size"
                      :data {:name ::beer}
                      :path "/api/ipa/large"
                      :path-params {:size "large"}})
                   (r/match-by-path router "/api/ipa/large")))
            (is (= (r/map->Match
                     {:template "/api/ipa/:size"
                      :data {:name ::beer}
                      :path "/api/ipa/large"
                      :path-params {:size "large"}})
                   (r/match-by-name router ::beer {:size "large"})))
            (is (= (r/map->Match
                     {:template "/api/ipa/:size"
                      :data {:name ::beer}
                      :path "/api/ipa/large"
                      :path-params {:size "large"}})
                   (r/match-by-name router ::beer {:size :large})))
            (is (= nil (r/match-by-name router "ILLEGAL")))
            (is (= [::beer] (r/route-names router)))

            (testing "name-based routing with missing parameters"
              (is (= (r/map->PartialMatch
                       {:template "/api/ipa/:size"
                        :data {:name ::beer}
                        :required #{:size}
                        :path-params nil})
                     (r/match-by-name router ::beer)))
              (is (r/partial-match? (r/match-by-name router ::beer)))
              (is (thrown-with-msg?
                    ExceptionInfo
                    #"^missing path-params for route /api/ipa/:size -> \#\{:size\}$"
                    (r/match-by-name! router ::beer))))))

        (testing "complex"
          (let [router (r/router
                         [["/:abba" ::abba]
                          ["/abba/1" ::abba2]
                          ["/:jabba/2" ::jabba2]
                          ["/:abba/:dabba/doo" ::doo]
                          ["/abba/:dabba/boo" ::boo]
                          ["/:jabba/:dabba/:doo/*foo" ::wild]]
                         {:router r})
                matches #(-> router (r/match-by-path %) :data :name)]
            (is (= ::abba (matches "/abba")))
            (is (= ::abba2 (matches "/abba/1")))
            (is (= ::jabba2 (matches "/abba/2")))
            (is (= ::doo (matches "/abba/1/doo")))
            (is (= ::boo (matches "/abba/1/boo")))
            (is (= ::wild (matches "/olipa/kerran/avaruus/vaan/ei/toista/kertaa"))))))

      r/linear-router :linear-router
      r/segment-router :segment-router
      r/mixed-router :mixed-router))

  (testing "routers handling static paths"
    (are [r name]
      (let [router (r/router ["/api" ["/ipa" ["/large" ::beer]]] {:router r})]
        (is (= name (r/router-name router)))
        (is (= [["/api/ipa/large" {:name ::beer} nil]]
               (r/routes router)))
        (is (map? (r/options router)))
        (is (= (r/map->Match
                 {:template "/api/ipa/large"
                  :data {:name ::beer}
                  :path "/api/ipa/large"
                  :path-params {}})
               (r/match-by-path router "/api/ipa/large")))
        (is (= (r/map->Match
                 {:template "/api/ipa/large"
                  :data {:name ::beer}
                  :path "/api/ipa/large"
                  :path-params {:size "large"}})
               (r/match-by-name router ::beer {:size "large"})))
        (is (= nil (r/match-by-name router "ILLEGAL")))
        (is (= [::beer] (r/route-names router)))

        (testing "can't be created with wildcard routes"
          (is (thrown-with-msg?
                ExceptionInfo
                #"can't create :lookup-router with wildcard routes"
                (r/lookup-router
                  (r/resolve-routes
                    ["/api/:version/ping"] {}))))))

      r/lookup-router :lookup-router
      r/single-static-path-router :single-static-path-router
      r/linear-router :linear-router
      r/segment-router :segment-router
      r/mixed-router :mixed-router))

  (testing "nil routes are allowed ans stripped"
    (is (= [] (r/routes (r/router nil))))
    (is (= [] (r/routes (r/router [nil [nil] [[nil nil nil]]]))))
    (is (= [["/ping" {} nil]] (r/routes (r/router [nil [nil] ["/ping"]]))))
    (is (= [["/ping" {} nil]] (r/routes (r/router [[[nil [nil] ["/ping"]]]])))))

  (testing "route coercion & compilation"

    (testing "custom compile"
      (let [compile-times (atom 0)
            coerce (fn [[path data] _]
                     (if-not (:invalid? data)
                       [path (assoc data :path path)]))
            compile (fn [[path data] _]
                      (swap! compile-times inc)
                      (constantly path))
            router (r/router
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
                 (map butlast (r/routes router)))))

        (testing "route match contains compiled handler"
          (is (= 2 @compile-times))
          (let [{:keys [result]} (r/match-by-path router "/api/pong")]
            (is result)
            (is (= "/api/pong" (result)))
            (is (= 2 @compile-times))))))

    (testing "default compile"
      (let [router (r/router ["/ping" (constantly "ok")])]
        (let [{:keys [result]} (r/match-by-path router "/ping")]
          (is result)
          (is (= "ok" (result)))))))

  (testing "custom router"
    (let [router (r/router ["/ping"] {:router (fn [_ _]
                                                (reify Router
                                                  (r/router-name [_]
                                                    ::custom)))})]
      (is (= ::custom (r/router-name router)))))

  (testing "bide sample"
    (let [routes [["/auth/login" :auth/login]
                  ["/auth/recovery/token/:token" :auth/recovery]
                  ["/workspace/:project-uuid/:page-uuid" :workspace/page]]
          expected [["/auth/login" {:name :auth/login}]
                    ["/auth/recovery/token/:token" {:name :auth/recovery}]
                    ["/workspace/:project-uuid/:page-uuid" {:name :workspace/page}]]]
      (is (= expected (r/resolve-routes routes {})))))

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
          router (r/router routes)]
      (is (= expected (r/resolve-routes routes {})))
      (is (= (r/map->Match
               {:template "/api/user/:id/:sub-id"
                :data {:mw [:api], :parameters {:id "String", :sub-id "String"}}
                :path "/api/user/1/2"
                :path-params {:id "1", :sub-id "2"}})
             (r/match-by-path router "/api/user/1/2"))))))

(deftest conflicting-routes-test
  (are [conflicting? data]
    (let [routes (r/resolve-routes data {})
          conflicts (-> routes (r/resolve-routes {}) (r/conflicting-routes))]
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
            ["/c" {}] #{["/*d" {}]}}
           (-> [["/a"] ["/:b"] ["/c"] ["/*d"]]
               (r/resolve-routes {})
               (r/conflicting-routes)))))

  (testing "router with conflicting routes"
    (testing "throws by default"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Router contains conflicting routes"
            (r/router
              [["/a"] ["/a"]]))))
    (testing "can be configured to ignore"
      (is (not (nil? (r/router [["/a"] ["/a"]] {:conflicts (constantly nil)})))))))
