(ns reitit.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r #?@(:cljs [:refer [Router]])]
            [reitit.impl :as impl])
  #?(:clj
     (:import (reitit.core Router)
              (clojure.lang ExceptionInfo))))

(deftest reitit-test

  (testing "routers handling wildcard paths"
    (are [r name]
      (testing "wild"

        (testing "simple"
          (let [router (r/router ["/api" ["/ipa" ["/:size" ::beer]]] {:router r})]
            (is (= name (r/router-name router)))
            (is (= [["/api/ipa/:size" {:name ::beer}]]
                   (r/routes router)))
            (is (map? (r/options router)))
            (is (= nil
                   (r/match-by-path router "/api")))
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

        (testing "decode %-encoded path params"
          (let [router (r/router [["/one-param-path/:param1" ::one]
                                  ["/two-param-path/:param1/:param2"]
                                  ["/catchall/*remaining-path"]] {:router r})
                decoded-params #(-> router (r/match-by-path %) :path-params)
                decoded-param1 #(-> (decoded-params %) :param1)
                decoded-remaining-path #(-> (decoded-params %) :remaining-path)]
            (is (= {:param1 "käki"} (:path-params (r/match-by-name router ::one {:param1 "käki"}))))
            (is (= "/one-param-path/k%C3%A4ki" (:path (r/match-by-name router ::one {:param1 "käki"}))))
            (is (= "foo bar" (decoded-param1 "/one-param-path/foo%20bar")))
            (is (= {:param1 "foo bar" :param2 "baz qux"} (decoded-params "/two-param-path/foo%20bar/baz%20qux")))
            (is (= "foo bar" (decoded-remaining-path "/catchall/foo%20bar")))
            (is (= "!#$&'()*+,/:;=?@[]"
                   (decoded-param1 "/one-param-path/%21%23%24%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D")))))

        (testing "complex"
          (let [router (r/router
                         [["/:abba" ::abba]
                          ["/abba/1" ::abba2]
                          ["/:jabba/2" ::jabba2]
                          ["/:abba/:dabba/doo" ::doo]
                          ["/abba/dabba/boo/baa" ::baa]
                          ["/abba/:dabba/boo" ::boo]
                          ["/:jabba/:dabba/:doo/:daa/*foo" ::wild]]
                         {:router r})
                by-path #(-> router (r/match-by-path %) :data :name)]
            (is (= ::abba (by-path "/abba")))
            (is (= ::abba2 (by-path "/abba/1")))
            (is (= ::jabba2 (by-path "/abba/2")))
            (is (= ::doo (by-path "/abba/1/doo")))
            (is (= ::boo (by-path "/abba/1/boo")))
            (is (= ::baa (by-path "/abba/dabba/boo/baa")))
            (is (= ::boo (by-path "/abba/dabba/boo")))
            (is (= ::wild (by-path "/olipa/kerran/avaruus/vaan/")))
            (is (= ::wild (by-path "/olipa/kerran/avaruus/vaan/ei/toista/kertaa")))))

        (testing "bracket-params"
          (testing "successful"
            (let [router (r/router
                           [["/{abba}" ::abba]
                            ["/abba/1" ::abba2]
                            ["/{jabba}/2" ::jabba2]
                            ["/{abba}/{dabba}/doo" ::doo]
                            ["/abba/dabba/boo/baa" ::baa]
                            ["/abba/{dabba}/boo" ::boo]
                            ["/{a/jabba}/{a.b/dabba}/{a.b.c/doo}/{a.b.c.d/daa}/{*foo/bar}" ::wild]
                            ["/files/file-{name}.html" ::html]
                            ["/files/file-{name}.json" ::json]
                            ["/{eskon}/{saum}/pium\u2215paum" ::loru]
                            ["/{🌈}🤔/🎈" ::emoji]
                            ["/extra-end}s-are/ok" ::bracket]]
                           {:router r})
                  by-path #(-> router (r/match-by-path %) ((juxt (comp :name :data) :path-params)))]
              (is (= [::abba {:abba "abba"}] (by-path "/abba")))
              (is (= [::abba2 {}] (by-path "/abba/1")))
              (is (= [::jabba2 {:jabba "abba"}] (by-path "/abba/2")))
              (is (= [::doo {:abba "abba", :dabba "1"}] (by-path "/abba/1/doo")))
              (is (= [::boo {:dabba "1"}] (by-path "/abba/1/boo")))
              (is (= [::baa {}] (by-path "/abba/dabba/boo/baa")))
              (is (= [::boo {:dabba "dabba"}] (by-path "/abba/dabba/boo")))
              (is (= [::wild {:a/jabba "olipa"
                              :a.b/dabba "kerran"
                              :a.b.c/doo "avaruus"
                              :a.b.c.d/daa "vaan"
                              :foo/bar "ei/toista/kertaa"}]
                     (by-path "/olipa/kerran/avaruus/vaan/ei/toista/kertaa")))
              (is (= [::html {:name "10"}] (by-path "/files/file-10.html")))
              (is (= [::loru {:eskon "viitan", :saum "aa"}] (by-path "/viitan/aa/pium\u2215paum")))
              (is (= [nil nil] (by-path "/ei/osu/pium/paum")))
              (is (= [::emoji {:🌈 "brackets"}] (by-path "/brackets🤔/🎈")))
              (is (= [::bracket {}] (by-path "/extra-end}s-are/ok")))))

          (testing "invalid syntax fails fast"
            (testing "unclosed brackets"
              (is (thrown-with-msg?
                    ExceptionInfo
                    #":reitit.trie/unclosed-brackets"
                    (r/router ["/kikka/{kukka"]))))
            (testing "multiple terminators"
              (is (thrown-with-msg?
                    ExceptionInfo
                    #":reitit.trie/multiple-terminators"
                    (r/router [["/{kukka}.json"]
                               ["/{kukka}-json"]]))))))

        (testing "empty path segments"
          (let [router (r/router
                         [["/items" ::list]
                          ["/items/:id" ::item]
                          ["/items/:id/:side" ::deep]]
                         {:router r})
                matches #(-> router (r/match-by-path %) :data :name)]
            (is (= ::list (matches "/items")))
            (is (= nil (matches "/items/")))
            (is (= ::item (matches "/items/1")))
            (is (= ::deep (matches "/items/1/2")))
            (is (= nil (matches "/items//2")))
            (is (= nil (matches ""))))))

      r/linear-router :linear-router
      r/trie-router :trie-router
      r/mixed-router :mixed-router
      r/quarantine-router :quarantine-router))

  (testing "routers handling static paths"
    (are [r name]
      (let [router (r/router ["/api" ["/ipa" ["/large" ::beer]]] {:router r})]
        (is (= name (r/router-name router)))
        (is (= [["/api/ipa/large" {:name ::beer}]]
               (r/routes router)))
        (is (map? (r/options router)))
        (is (= nil
               (r/match-by-path router "/api")))
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
                  (impl/resolve-routes
                    ["/api/:version/ping"]
                    (r/default-router-options)))))))

      r/lookup-router :lookup-router
      r/single-static-path-router :single-static-path-router
      r/linear-router :linear-router
      r/trie-router :trie-router
      r/mixed-router :mixed-router
      r/quarantine-router :quarantine-router))

  (testing "nil routes are stripped"
    (is (= [] (r/routes (r/router nil))))
    (is (= [] (r/routes (r/router [nil ["/ping"]]))))
    (is (= [] (r/routes (r/router [nil [nil] [[nil nil nil]]]))))
    (is (= [] (r/routes (r/router ["/ping" [nil "/pong"]])))))

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
                 (r/routes router))))

        (testing "route match contains compiled handler"
          (is (= 2 @compile-times))
          (let [{:keys [result]} (r/match-by-path router "/api/pong")]
            (is result)
            (is (= "/api/pong" (result)))
            (is (= 2 @compile-times))))))

    (testing "default compile"
      (let [router (r/router ["/ping" (constantly "ok")])
            {:keys [result]} (r/match-by-path router "/ping")]
        (is result)
        (is (= "ok" (result))))))

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
      (is (= expected (impl/resolve-routes routes (r/default-router-options))))))

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
      (is (= expected (impl/resolve-routes routes (r/default-router-options))))
      (is (= (r/map->Match
               {:template "/api/user/:id/:sub-id"
                :data {:mw [:api], :parameters {:id "String", :sub-id "String"}}
                :path "/api/user/1/2"
                :path-params {:id "1", :sub-id "2"}})
             (r/match-by-path router "/api/user/1/2"))))))

(deftest conflicting-routes-test
  (testing "path conflicts"
    (are [conflicting? data]
      (let [routes (impl/resolve-routes data (r/default-router-options))
            conflicts (-> routes
                          (impl/resolve-routes (r/default-router-options))
                          (impl/path-conflicting-routes nil))]
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
                 (impl/resolve-routes (r/default-router-options))
                 (impl/path-conflicting-routes nil)))))

    (testing "router with conflicting routes"
      (testing "throws by default"
        (is (thrown-with-msg?
              ExceptionInfo
              #"Router contains conflicting route paths"
              (r/router
                [["/a"] ["/a"]]))))
      (testing "can be configured to ignore with route data"
        (are [paths expected]
          (let [router (r/router paths)]
            (is (not (nil? router)))
            (is (= expected (r/router-name router))))
          [["/a" {:conflicting true}]
           ["/a" {:conflicting true}]] :quarantine-router
          [["/a" {:conflicting true}]
           ["/:b" {:conflicting true}]
           ["/c" {:conflicting true}]
           ["/*d" {:conflicting true}]] :quarantine-router
          [["/:a"
            ["/:b" {:conflicting true}]
            ["/:c" {:conflicting true}]
            ["/:d"
             ["/:e" {:conflicting true}]
             ["/:f" {:conflicting true}]]]] :quarantine-router
          [["/:a" {:conflicting true}
            ["/:b"]
            ["/:c"]
            ["/:d"
             ["/:e"]
             ["/:f"]]]] :quarantine-router)
        (testing "unmarked path conflicts throw"
          (are [paths]
            (is (thrown-with-msg?
                  ExceptionInfo
                  #"Router contains conflicting route paths"
                  (r/router paths)))
            [["/a"] ["/a" {:conflicting true}]]
            [["/a" {:conflicting true}] ["/a"]])))
      (testing "can be configured to ignore with router option"
        (is (not (nil? (r/router [["/a"] ["/a"]] {:conflicts nil})))))))

  (testing "name conflicts"
    (testing "router with conflicting routes always throws"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Router contains conflicting route names"
            (r/router
              [["/1" ::1] ["/2" ::1]]))))))

(deftest match->path-test
  (let [router (r/router ["/:a/:b" ::route])]
    (is (= "/olipa/kerran"
           (-> router
               (r/match-by-name! ::route {:a "olipa", :b "kerran"})
               (r/match->path))))
    (is (= "/olipa/kerran"
           (-> router
               (r/match-by-name! ::route {:a "olipa", :b "kerran"})
               (r/match->path {}))))
    (is (= "/olipa/kerran?iso=p%C3%B6ril%C3%A4inen"
           (-> router
               (r/match-by-name! ::route {:a "olipa", :b "kerran"})
               (r/match->path {:iso "pöriläinen"}))))))

(deftest sequential-routes
  (testing "sequential child routes work"
    (is (= [["/api/0" {}]
            ["/api/1" {}]]
           (-> ["/api"
                (for [i (range 2)]
                  [(str "/" i)])]
               (r/router)
               (r/routes)))))
  (testing "sequential route definition fails"
    (is (thrown?
          #?(:clj Exception, :cljs js/Error)
          (-> ["/api"
               (list "/ipa")]
              (r/router))))))

(defrecord Named [n]
  r/Expand
  (r/expand [_ _] {:name n}))

(deftest default-expand-test
  (let [router (r/router ["/endpoint" (->Named :kikka)])]
    (is (= [["/endpoint" {:name :kikka}]]
           (r/routes router)))))

(deftest routing-order-test-229
  (let [router (r/router
                 [["/" :root]
                  ["/" {:name :create :method :post}]]
                 {:conflicts nil})
        router2 (r/router
                  [["/*a" :root]
                   ["/:a/b/c/d" {:name :create :method :post}]]
                  {:conflicts nil})]
    (is (= :root (-> (r/match-by-path router "/") :data :name)))
    (is (= :root (-> (r/match-by-path router2 "/") :data :name)))))
