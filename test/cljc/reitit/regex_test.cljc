(ns reitit.regex-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [reitit.coercion :as coercion]
            [reitit.coercion.spec :as rss]
            [reitit.core :as r]
            [reitit.regex :as rt.regex]))

(defn re-str
  "Walks a data structure to convert any regex to its string representation."
  [x]
  (walk/postwalk
   (fn [form]
     (if (rt.regex/regex? form)
       (rt.regex/pattern-str form)
       form))
   x))

(defn process-match
  "Removes data which is not easy to test for equality with."
  [m]
  (some-> m
          (update :data dissoc :coercion :conflicting)
          re-str))

(def router
  (r/router
   ["/" {:conflicting true}
    [["" ::home]
     ["inbox" ::inbox]
     ["teams" ::teams]
     [":#item-id" {:name ::item
                   :path-regex {:item-id #"[a-z]{16,20}"}}]
     ["teams/:#team-id-b58/members" {:name ::->members
                                     :path-regex {:team-id-b58 #"[a-z]"}}]
     ["teams/:#team-id-b58/guests" {:name ::->guests
                                    :path-regex {:team-id-b58 #"[a-z]"}}]]]
   {:data {:coercion rss/coercion}
    :router rt.regex/regex-router}))

(deftest regex-router-test
  (is (= :regex-router (r/router-name router)))
  (is (= rt.regex/regex-router (:router (r/options router))))

  (is (= [{:name ::home
           :path-regex nil
           :pattern #?(:clj "^/?$"
                       :cljs "^\\/?$")}
          {:name ::inbox
           :path-regex nil
           :pattern #?(:clj "^/\\Qinbox\\E$"
                       :cljs "^\\/inbox$")}
          {:name ::teams
           :path-regex nil
           :pattern #?(:clj "^/\\Qteams\\E$"
                       :cljs "^\\/teams$")}
          {:name ::item
           :path-regex {:item-id "[a-z]{16,20}"}
           :pattern #?(:clj "^/([a-z]{16,20})$"
                       :cljs "^\\/([a-z]{16,20})$")}
          {:name ::->members
           :path-regex {:team-id-b58 "[a-z]"}
           :pattern #?(:clj "^/\\Qteams\\E/([a-z])/\\Qmembers\\E$"
                       :cljs "^\\/teams\\/([a-z])\\/members$")}
          {:name ::->guests
           :path-regex {:team-id-b58 "[a-z]"}
           :pattern #?(:clj "^/\\Qteams\\E/([a-z])/\\Qguests\\E$"
                       :cljs "^\\/teams\\/([a-z])\\/guests$")}]
         (->> (r/compiled-routes router)
              (map (fn [route]
                     {:name (get-in route [:route-data :name])
                      :path-regex (get-in route [:route-data :path-regex])
                      :pattern (:pattern route)}))
              re-str))))

(deftest regex-match-by-path-test
  (testing "Basic path matching"
    (is (= (r/map->Match {:path "/"
                          :path-params {}
                          :data {:name ::home}
                          :template "/"
                          :result nil})
           (process-match (r/match-by-path router "/"))))

    (is (= (r/map->Match {:path "/inbox"
                          :path-params {}
                          :data {:name ::inbox}
                          :template "/inbox"
                          :result nil})
           (process-match (r/match-by-path router "/inbox"))))

    (is (= (r/map->Match {:path "/teams"
                          :path-params {}
                          :data {:name ::teams}
                          :template "/teams"
                          :result nil})
           (process-match (r/match-by-path router "/teams")))))

  (testing "Path with regex parameter"
    (let [valid-id "abcdefghijklmnopq"]
      (is (= (r/map->Match {:path (str "/" valid-id)
                            :path-params {:item-id valid-id}
                            :data {:name ::item
                                   :path-regex {:item-id "[a-z]{16,20}"}}
                            :template "/:#item-id"
                            :result nil})
             (process-match (r/match-by-path router (str "/" valid-id))))))

    (is (nil? (r/match-by-path router "/abcdefg")) "Too short")
    (is (nil? (r/match-by-path router "/abcdefghijklmnopqRST")) "Contains uppercase")
    (is (nil? (r/match-by-path router "/abcdefghijklmn1234")) "Contains digits"))

  (testing "Nested path with parameter"
    (is (= (r/map->Match {:path "/teams/a/members"
                          :path-params {:team-id-b58 "a"}
                          :data {:name ::->members
                                 :path-regex {:team-id-b58 "[a-z]"}}
                          :template "/teams/:#team-id-b58/members"
                          :result nil})
           (process-match (r/match-by-path router "/teams/a/members"))))

    (is (nil? (r/match-by-path router "/teams/abc/members")) "Multiple characters")
    (is (nil? (r/match-by-path router "/teams/1/members")) "Digit instead of letter"))

  (testing "Non-matching paths"
    (is (nil? (r/match-by-path router "/unknown")))
    (is (nil? (r/match-by-path router "/team")))
    (is (nil? (r/match-by-path router "/teams/extra/segments/here")))))

(deftest regex-match-by-name-test
  (testing "Basic match-by-name functionality"
    (is (= (r/map->Match {:path "/"
                          :path-params {}
                          :data {:name ::home}
                          :template "/"
                          :result nil})
           (process-match (r/match-by-name router ::home))))

    (is (= (r/map->Match {:path "/inbox"
                          :path-params {}
                          :data {:name ::inbox}
                          :template "/inbox"
                          :result nil})
           (process-match (r/match-by-name router ::inbox))))

    (is (= (r/map->Match {:path "/teams"
                          :path-params {}
                          :data {:name ::teams}
                          :template "/teams"
                          :result nil})
           (process-match (r/match-by-name router ::teams))))

    (let [valid-id "abcdefghijklmnopq"]
      (is (= (r/map->Match {:path (str "/" valid-id)
                            :path-params {:item-id valid-id}
                            :data {:name ::item
                                   :path-regex {:item-id "[a-z]{16,20}"}}
                            :template "/:#item-id"
                            :result nil})
             (process-match (r/match-by-name router ::item {:item-id valid-id})))))

    (is (= (r/map->Match {:path "/teams/a/members"
                          :path-params {:team-id-b58 "a"}
                          :data {:name ::->members
                                 :path-regex {:team-id-b58 "[a-z]"}}
                          :template "/teams/:#team-id-b58/members"
                          :result nil})
           (process-match (r/match-by-name router ::->members {:team-id-b58 "a"})))))

  (testing "Path round-trip matching"
    (let [valid-id "abcdefghijklmnopq"
          match (process-match (r/match-by-name router ::item {:item-id valid-id}))
          path (:path match)]
      (is (some? path))
      (is (= match (process-match (r/match-by-path router path)))))

    (let [match (process-match (r/match-by-name router ::->members {:team-id-b58 "a"}))
          path (:path match)]
      (is (some? path))
      (is (= match (process-match (r/match-by-path router path))))))

  (testing "Partial match with missing parameters"
    (let [partial-match (process-match (r/match-by-name router ::item {}))]
      (is (instance? reitit.core.PartialMatch partial-match))
      (is (= #{:item-id} (:required partial-match)))
      (is (= (r/map->PartialMatch {:template "/:#item-id"
                                   :data {:name ::item
                                          :path-regex {:item-id "[a-z]{16,20}"}}
                                   :path-params {}
                                   :required #{:item-id}
                                   :result nil})
             partial-match)))

    (let [partial-match (r/match-by-name router ::->members {})]
      (is (instance? reitit.core.PartialMatch partial-match))
      (is (= #{:team-id-b58} (:required partial-match)))))

  (testing "Match with invalid parameters"
    (let [match (r/match-by-name router ::item {:item-id "too-short"})
          path (:path match)]
      (is (instance? reitit.core.Match match))
      (is (= "/too-short" path))
      (is (nil? (r/match-by-path router path)))))

  (testing "Non-existent routes"
    (is (nil? (r/match-by-name router ::non-existent)))))

(deftest regex-router-edge-cases-test
  (testing "Empty router"
    (let [empty-router (rt.regex/regex-router [])]
      (is (nil? (r/match-by-path empty-router "/any/path")))))

  (testing "Handling trailing slashes"
    (is (nil? (r/match-by-path router "/inbox/")))

    (let [router-with-trailing-slash (rt.regex/regex-router [["inbox/" ::inbox-with-slash]])]
      (is (nil? (r/match-by-path router-with-trailing-slash "/inbox/")))
      (is (some? (r/match-by-path router-with-trailing-slash "/inbox")))))

  (testing "Complex path patterns"
    (let [complex-router (rt.regex/regex-router
                          [["articles/:#year/:#month/:#slug"
                            {:name ::article
                             :path-regex {:year #"\d{4}"
                                          :month #"\d{2}"
                                          :slug #"[a-z0-9\-]+"}}]
                           ["files/:path*"
                            {:name ::file-path}]])]
      (let [match (r/match-by-name complex-router ::article
                                   {:year "2023" :month "02" :slug "test-article"})]
        (is (instance? reitit.core.Match match))
        (is (= "/articles/2023/02/test-article" (:path match))))

      (let [match (r/match-by-path complex-router "/articles/2023/02/test-article")]
        (is (some? match))
        (is (= {:year "2023", :month "02", :slug "test-article"}
               (:path-params match))))

      (is (nil? (r/match-by-path complex-router "/articles/202/02/test-article")))

      (let [partial-match (r/match-by-name complex-router ::article {:year "2023"})]
        (is (instance? reitit.core.PartialMatch partial-match))
        (is (= #{:month :slug} (:required partial-match)))))))

(deftest custom-router-features-test
  (testing "Router information access"
    (is (= :regex-router (r/router-name router)))
    (is (seq (r/routes router)))
    (is (= #{::home ::item ::inbox ::teams ::->members ::->guests}
           (set (r/route-names router)))))

  (testing "Compiled routes access"
    (let [compiled (r/compiled-routes router)]
      (is (seq compiled))
      (is (every? :pattern compiled))
      (is (every? :route-data compiled)))))

(deftest result-threading-test
  (let [coercion-router (r/router
                         [["/users/:id" {:name ::user
                                         :parameters {:path {:id int?}}}]]
                         {:data {:coercion rss/coercion}
                          :compile coercion/compile-request-coercers
                          :router rt.regex/regex-router})]
    (testing "compiled :result is threaded through to matches so coercion works"
      (let [match (r/match-by-path coercion-router "/users/42")]
        (is (some? (:result match)))
        (is (= {:path {:id 42}}
               (coercion/coerce! match)))))

    (testing "match-by-name also carries :result"
      (is (some? (:result (r/match-by-name coercion-router ::user {:id 7})))))))

(deftest nested-capture-groups-test
  (testing "user regex capturing groups do not desync params"
    (let [router (rt.regex/regex-router
                  [["/post/:#kind" {:name ::post
                                    :path-regex {:kind #"(news|blog)-\d+"}}]
                   ["/x/:#a/:#b" {:name ::two
                                  :path-regex {:a #"(foo|bar)"
                                               :b #"\d+"}}]])]
      (is (= {:kind "news-42"}
             (:path-params (r/match-by-path router "/post/news-42"))))
      (is (= {:a "foo" :b "123"}
             (:path-params (r/match-by-path router "/x/foo/123"))))
      (is (nil? (r/match-by-path router "/x/baz/123"))))))

(deftest url-encoding-test
  (let [router (rt.regex/regex-router
                [["/:slug" {:name ::slug}]
                 ["/files/*path" {:name ::files}]])]
    (testing "path params are URL-decoded on match"
      (is (= {:slug "hello world"}
             (:path-params (r/match-by-path router "/hello%20world")))))

    (testing "param values are URL-encoded when generating a path"
      (is (= "/a%20b%2Fc"
             (:path (r/match-by-name router ::slug {:slug "a b/c"})))))

    (testing "catch-all values are encoded per segment, preserving slashes"
      (is (= "/files/a%20b/c%20d"
             (:path (r/match-by-name router ::files {:path "a b/c d"})))))

    (testing "name to path to match round-trips through encoding"
      (let [path (:path (r/match-by-name router ::slug {:slug "a b"}))]
        (is (= "/a%20b" path))
        (is (= {:slug "a b"} (:path-params (r/match-by-path router path)))))
      (let [path (:path (r/match-by-name router ::files {:path "x/y z"}))]
        (is (= "/files/x/y%20z" path))
        (is (= {:path "x/y z"} (:path-params (r/match-by-path router path)))))))

  (testing "a :# regex param's value is emitted verbatim"
    (let [router (rt.regex/regex-router
                  [["/components/:#component-id"
                    {:name ::component
                     :path-regex {:component-id #"[0-9a-zA-Z-/]+"}}]])]
      (is (= "/components/fancy/input"
             (:path (r/match-by-name router ::component {:component-id "fancy/input"}))))
      (let [path (:path (r/match-by-name router ::component {:component-id "fancy/input"}))]
        (is (= {:component-id "fancy/input"}
               (:path-params (r/match-by-path router path))))))))

(deftest linear-matching-test
  (testing "matching is linear and first-match-wins, in declaration order"
    (let [router (rt.regex/regex-router
                  [["/inbox" {:name ::inbox}]
                   ["/special" {:name ::special}]
                   ["/:slug" {:name ::slug}]])]
      (is (= ::inbox (-> (r/match-by-path router "/inbox") :data :name)))
      (is (= ::special (-> (r/match-by-path router "/special") :data :name)))
      (is (= ::slug (-> (r/match-by-path router "/anything-else") :data :name)))))

  (testing "a broad route declared first shadows later, more specific routes"
    (let [router (rt.regex/regex-router
                  [["/:slug" {:name ::slug}]
                   ["/inbox" {:name ::inbox}]])]
      (is (= ::slug (-> (r/match-by-path router "/inbox") :data :name)))))

  (testing "catch-all declared after explicit routes lets the explicit route win"
    (let [router (rt.regex/regex-router
                  [["/files/:a/:b" {:name ::explicit}]
                   ["/files/*path" {:name ::catch-all}]])]
      (is (= ::explicit (-> (r/match-by-path router "/files/x/y") :data :name)))
      (is (= ::catch-all (-> (r/match-by-path router "/files/x/y/z") :data :name))))))

(deftest catch-all-test
  (let [router (rt.regex/regex-router
                [["/files/*path" {:name ::files}]
                 ["/c/{*rest}" {:name ::bracket-catch}]])]
    (testing "*name catch-all matches the remainder of the path including slashes"
      (is (= {:path "a/b/c.txt"}
             (:path-params (r/match-by-path router "/files/a/b/c.txt"))))
      (is (= ::files (-> (r/match-by-path router "/files/a/b/c.txt") :data :name))))

    (testing "{*name} bracket catch-all is equivalent"
      (is (= {:rest "x/y/z"}
             (:path-params (r/match-by-path router "/c/x/y/z")))))

    (testing "catch-all matches a single trailing segment"
      (is (= {:path "readme"}
             (:path-params (r/match-by-path router "/files/readme")))))))

(deftest bracket-syntax-test
  (testing "{param} is treated as a wildcard segment, like :param"
    (let [router (rt.regex/regex-router
                  [["/u/{id}" {:name ::user}]
                   ["/u/{id}/posts" {:name ::posts}]])]
      (is (= {:id "99"}
             (:path-params (r/match-by-path router "/u/99"))))
      (is (= {:id "99"}
             (:path-params (r/match-by-path router "/u/99/posts"))))
      (is (= "/u/abc" (:path (r/match-by-name router ::user {:id "abc"})))))))

(deftest compatibility-test
  (testing "create-regex-router remains as a compatibility wrapper"
    (let [router (rt.regex/create-regex-router
                  [["strings/:#id" {:name ::string-id
                                    :path-regex {:id "\\d+"}}]])]
      (is (= {:id "123"}
             (:path-params (r/match-by-path router "/strings/123"))))
      (is (nil? (r/match-by-path router "/strings/abc")))))

  (testing "regex segments require path regex data"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo
                    :cljs cljs.core.ExceptionInfo)
                 (rt.regex/regex-router [[":#id" {:name ::missing-regex}]]))))

  (testing "plain params are unconstrained by :path-regex"
    (let [router (rt.regex/regex-router
                  [[":id" {:name ::plain-id
                           :path-regex {:id #"\d+"}}]])]
      (is (= {:id "abc"}
             (:path-params (r/match-by-path router "/abc"))))))

  (testing "legacy :parameters :path regexes remain supported"
    (let [router (rt.regex/create-regex-router
                  [[":id" {:name ::legacy-id
                           :parameters {:path {:id #"\d+"}}}]])]
      (is (= {:id "123"}
             (:path-params (r/match-by-path router "/123"))))
      (is (nil? (r/match-by-path router "/abc"))))))
