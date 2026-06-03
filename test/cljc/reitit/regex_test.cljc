(ns reitit.regex-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
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

(defn re-=
  "A custom equality function that handles regex patterns specially.
   Returns true if a and b are equal, with special handling for regex patterns.
   Also handles comparing records by their map representation."
  [a b]
  (cond
    ;; Handle record comparison by using their map representation
    (and (record? a)
         (record? b))
    (re-= (into {} a) (into {} b))

    ;; If both are regex patterns, compare their string representations
    (and (rt.regex/regex? a)
         (rt.regex/regex? b))
    (= (rt.regex/pattern-str a) (rt.regex/pattern-str b))

    ;; If one is a regex and the other isn't, they're not equal
    (or (rt.regex/regex? a)
        (rt.regex/regex? b))
    false

    ;; For maps, compare each key-value pair using regex-aware-equals
    (and (map? a) (map? b))
    (and (= (set (keys a)) (set (keys b)))
         (every? #(re-= (get a %) (get b %)) (keys a)))

    ;; For sequences, compare each element using regex-aware-equals
    (and (sequential? a) (sequential? b))
    (and (= (count a) (count b))
         (every? identity (map re-= a b)))

    ;; For sets, convert to sequences and compare
    (and (set? a) (set? b))
    (re-= (seq a) (seq b))

    ;; For everything else, use regular equality
    :else
    (= a b)))

(defn process-match [m]
  (some-> m
          (update :data dissoc :conflicting)
          re-str))

(def regex-routes
  [["" ::home]
   [":#item-id" {:name ::item
                 :path-regex {:item-id #"[a-z]{16,20}"}}]
   ["inbox" ::inbox]
   ["teams" ::teams]
   ["teams/:#team-id-b58/members" {:name ::->members
                                   :path-regex {:team-id-b58 #"[a-z]"}}]])

(def routes
  (rt.regex/create-regex-router regex-routes))

(def core-router
  (r/router
   ["/" {:conflicting true}
    regex-routes]
   {:router rt.regex/regex-router}))

(deftest regex-router-test
  (testing "Compiled regex routes"
    (is (= :regex-router (r/router-name routes)))
    (is (= {} (r/options routes)))

    (is (= [{:name ::home
             :path-regex nil
             :pattern #?(:clj "^/?$"
                         :cljs "^\\/?$")}
            {:name ::item
             :path-regex {:item-id "[a-z]{16,20}"}
             :pattern #?(:clj "^/([a-z]{16,20})$"
                         :cljs "^\\/([a-z]{16,20})$")}
            {:name ::inbox
             :path-regex nil
             :pattern #?(:clj "^/\\Qinbox\\E$"
                         :cljs "^\\/inbox$")}
            {:name ::teams
             :path-regex nil
             :pattern #?(:clj "^/\\Qteams\\E$"
                         :cljs "^\\/teams$")}
            {:name ::->members
             :path-regex {:team-id-b58 "[a-z]"}
             :pattern #?(:clj "^/\\Qteams\\E/([a-z])/\\Qmembers\\E$"
                         :cljs "^\\/teams\\/([a-z])\\/members$")}]
           (->> (r/compiled-routes routes)
                (map (fn [route]
                       {:name (get-in route [:route-data :name])
                        :path-regex (get-in route [:route-data :path-regex])
                        :pattern (:pattern route)}))
                re-str))))

  (testing "Works as a reitit.core/router implementation"
    (let [valid-id "abcdefghijklmnopq"]
      (is (= :regex-router (r/router-name core-router)))
      (is (= rt.regex/regex-router (:router (r/options core-router))))
      (is (= (r/map->Match {:path (str "/" valid-id)
                            :path-params {:item-id valid-id}
                            :data {:name ::item
                                   :path-regex {:item-id "[a-z]{16,20}"}}
                            :template "/:#item-id"
                            :result nil})
             (process-match (r/match-by-path core-router (str "/" valid-id)))))
      (is (nil? (r/match-by-path core-router "/inbox/")))))

  (testing "String regex values are supported"
    (let [router (rt.regex/regex-router
                  [["strings/:#id" {:name ::string-id
                                    :path-regex {:id "\\d+"}}]])]
      (is (= {:id "123"}
             (:path-params (r/match-by-path router "/strings/123"))))
      (is (nil? (r/match-by-path router "/strings/abc")))))

  (testing "Regex segments require path regex data"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo
                    :cljs cljs.core.ExceptionInfo)
                 (rt.regex/regex-router [[":#id" {:name ::missing-regex}]]))))

  (testing "Plain params are unconstrained by :path-regex"
    (let [router (rt.regex/regex-router
                  [[":id" {:name ::plain-id
                           :path-regex {:id #"\d+"}}]])]
      (is (= {:id "abc"}
             (:path-params (r/match-by-path router "/abc"))))))

  (testing "Legacy :parameters :path regexes remain supported"
    (let [router (rt.regex/create-regex-router
                  [[":id" {:name ::legacy-id
                           :parameters {:path {:id #"\d+"}}}]])]
      (is (= {:id "123"}
             (:path-params (r/match-by-path router "/123"))))
      (is (nil? (r/match-by-path router "/abc")))))

  (testing "Compiled route results are preserved"
    (let [router (rt.regex/regex-router
                  [["result" {:name ::with-result} ::compiled-result]])]
      (is (= ::compiled-result
             (:result (r/match-by-path router "/result")))))))

(deftest regex-match-by-path-test
  (testing "Basic path matching"
    (is (= (r/map->Match {:path "/"
                          :path-params {}
                          :data {:name ::home}
                          :template "/"
                          :result nil})
           (r/match-by-path routes "/")))

    (is (= (r/map->Match {:path "/inbox"
                          :path-params {}
                          :data {:name ::inbox}
                          :template "/inbox"
                          :result nil})
           (r/match-by-path routes "/inbox")))

    (is (= (r/map->Match {:path "/teams"
                          :path-params {}
                          :data {:name ::teams}
                          :template "/teams"
                          :result nil})
           (r/match-by-path routes "/teams"))))

  (testing "Path with regex parameter"
    (let [valid-id "abcdefghijklmnopq"] ; 17 lowercase letters
      (is (re-= (r/map->Match {:path (str "/" valid-id)
                               :path-params {:item-id valid-id}
                               :data {:name ::item
                                      :path-regex {:item-id #"[a-z]{16,20}"}},
                               :template "/:#item-id"
                               :result nil})
                (r/match-by-path routes (str "/" valid-id)))))

    ;; Invalid parameter cases
    (is (nil? (r/match-by-path routes "/abcdefg")) "Too short")
    (is (nil? (r/match-by-path routes "/abcdefghijklmnopqRST")) "Contains uppercase")
    (is (nil? (r/match-by-path routes "/abcdefghijklmn1234")) "Contains digits"))

  (testing "Nested path with parameter"
    (is (re-= (r/map->Match {:path "/teams/a/members"
                             :path-params {:team-id-b58 "a"}
                             :data {:name ::->members
                                    :path-regex {:team-id-b58 #"[a-z]"}}
                             :template "/teams/:#team-id-b58/members"
                             :result nil})
              (r/match-by-path routes "/teams/a/members")))

    (is (nil? (r/match-by-path routes "/teams/abc/members")) "Multiple characters")
    (is (nil? (r/match-by-path routes "/teams/1/members")) "Digit instead of letter"))

  (testing "Non-matching paths"
    (is (nil? (r/match-by-path routes "/unknown")))
    (is (nil? (r/match-by-path routes "/team"))) ; 'team' not 'teams'
    (is (nil? (r/match-by-path routes "/teams/extra/segments/here")))))

(deftest regex-match-by-name-test
  (testing "Basic match-by-name functionality"
    ;; Root path
    (is (re-= (r/map->Match {:path "/"
                             :path-params {}
                             :data {:name ::home}
                             :template "/"
                             :result nil})
              (r/match-by-name routes ::home)))

    ;; Static paths
    (is (re-= (r/map->Match {:path "/inbox"
                             :path-params {}
                             :data {:name ::inbox}
                             :template "/inbox"
                             :result nil})
              (r/match-by-name routes ::inbox)))

    (is (re-= (r/map->Match {:path "/teams"
                             :path-params {}
                             :data {:name ::teams}
                             :template "/teams"
                             :result nil})
              (r/match-by-name routes ::teams)))

    ;; Path with parameter
    (let [valid-id "abcdefghijklmnopq"]
      (is (re-= (r/map->Match {:path (str "/" valid-id)
                               :path-params {:item-id valid-id}
                               :data {:name ::item
                                      :path-regex {:item-id #"[a-z]{16,20}"}}
                               :template "/:#item-id"
                               :result nil})
                (r/match-by-name routes ::item {:item-id valid-id}))))

    ;; Nested path with parameter
    (is (re-= (r/map->Match {:path "/teams/a/members"
                             :path-params {:team-id-b58 "a"}
                             :data {:name ::->members
                                    :path-regex {:team-id-b58 #"[a-z]"}}
                             :template "/teams/:#team-id-b58/members"
                             :result nil})
              (r/match-by-name routes ::->members {:team-id-b58 "a"}))))

  (testing "Path round-trip matching"
    ;; Test that paths generated by match-by-name can be successfully matched by match-by-path
    (let [valid-id "abcdefghijklmnopq"
          match (r/match-by-name routes ::item {:item-id valid-id})
          path (:path match)]

      (is (some? path) "Should generate a valid path")
      (is (re-= match (r/match-by-path routes path))
          "match-by-path should find the same route that generated the path"))

    (let [match (r/match-by-name routes ::->members {:team-id-b58 "a"})
          path (:path match)]

      (is (some? path) "Should generate a valid path")
      (is (re-= match (r/match-by-path routes path))
          "match-by-path should find the same route that generated the path")))

  (testing "Partial match with missing parameters"
    ;; Test that routes with missing parameters return PartialMatch
    (let [partial-match (r/match-by-name routes ::item {})]
      (is (instance? reitit.core.PartialMatch partial-match)
          "Should return a PartialMatch when params are missing")
      (is (= #{:item-id} (:required partial-match))
          "PartialMatch should indicate the required parameters")
      (is (re-= (r/map->PartialMatch {:template "/:#item-id"
                                      :data {:name ::item
                                             :path-regex {:item-id #"[a-z]{16,20}"}}
                                      :path-params {}
                                      :required #{:item-id}
                                      :result nil})
                partial-match)))

    ;; Test for a nested path with missing parameters
    (let [partial-match (r/match-by-name routes ::->members {})]
      (is (instance? reitit.core.PartialMatch partial-match)
          "Should return a PartialMatch for nested paths too")
      (is (= #{:team-id-b58} (:required partial-match))
          "PartialMatch should indicate the required parameters")))

  (testing "Match with invalid parameters"
    ;; Invalid parameters (that don't match the regex) still produce a Match
    (let [match (r/match-by-name routes ::item {:item-id "too-short"})
          path (:path match)]

      (is (instance? reitit.core.Match match)
          "Should produce a Match even with invalid parameters")
      (is (= "/too-short" path)
          "Path should contain the provided parameter value")
      (is (nil? (r/match-by-path routes path))
          "Path with invalid parameter shouldn't be matchable by match-by-path")))

  (testing "Non-existent routes"
    (is (nil? (r/match-by-name routes ::non-existent))
        "Should return nil for non-existent routes")))

(deftest regex-router-edge-cases-test
  (testing "Empty router"
    (let [empty-router (rt.regex/create-regex-router [])]
      (is (nil? (r/match-by-path empty-router "/any/path")))))

  (testing "Handling trailing slashes"
    (is (nil? (r/match-by-path routes "/inbox/")))

    (let [router-with-trailing-slash (rt.regex/create-regex-router [["inbox/" ::inbox-with-slash]])]
      (is (nil? (r/match-by-path router-with-trailing-slash "/inbox/")))
      (is (some? (r/match-by-path router-with-trailing-slash "/inbox")))))

  (testing "Complex path patterns"
    (let [complex-router (rt.regex/create-regex-router
                           [["articles/:#year/:#month/:#slug"
                             {:name ::article
                              :path-regex {:year #"\d{4}"
                                           :month #"\d{2}"
                                           :slug #"[a-z0-9\-]+"}}]
                            ["files/:path*"
                             {:name ::file-path}]])]

      ;; Test article route with valid params
      (let [match (r/match-by-name complex-router ::article
                                   {:year "2023" :month "02" :slug "test-article"})]
        (is (instance? reitit.core.Match match)
            "Should return a Match for complex routes with valid params")
        (is (= "/articles/2023/02/test-article" (:path match))
            "Path should be constructed correctly"))

      ;; Test match-by-path with the generated path
      (let [match (r/match-by-path complex-router "/articles/2023/02/test-article")]
        (is (some? match)
            "Should match a valid article path")
        (is (= {:year "2023", :month "02", :slug "test-article"}
               (:path-params match))
            "Should extract all parameters correctly"))

      ;; Test invalid path
      (is (nil? (r/match-by-path complex-router "/articles/202/02/test-article"))
          "Should not match an invalid year (3 digits)")

      ;; Test partial params
      (let [partial-match (r/match-by-name complex-router ::article {:year "2023"})]
        (is (instance? reitit.core.PartialMatch partial-match)
            "Should return PartialMatch when some params are missing")
        (is (= #{:month :slug} (:required partial-match))
            "Should indicate which params are missing")))))

(deftest custom-router-features-test
  (testing "Router information access"
    ;; Test that router information methods work properly
    (is (= :regex-router (r/router-name routes))
        "Should return the correct router name")

    (is (seq (r/routes routes))
        "Should return the list of routes")

    (is (= (set [::home ::item ::inbox ::teams ::->members])
           (set (r/route-names routes)))
        "Should return all route names"))

  (testing "Compiled routes access"
    (let [compiled (r/compiled-routes routes)]
      (is (seq compiled)
          "Should return compiled routes")
      (is (every? :pattern compiled)
          "Every compiled route should have a pattern")
      (is (every? :route-data compiled)
          "Every compiled route should have route data"))))
