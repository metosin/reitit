(ns reitit.frontend.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.coercion :as rc]
            [schema.core :as s]
            [reitit.coercion.schema :as rsc]
            [reitit.frontend.test-utils :refer [capture-console]]))

(defn m [x]
  (assoc x :data nil :result nil))

(deftest match-by-path-test
  (testing "simple"
    (let [router (r/router ["/"
                            ["" ::frontpage]
                            ["foo" ::foo]
                            ["bar" ::bar]])]
      (is (= (r/map->Match
               {:template "/"
                :data {:name ::frontpage}
                :path-params {}
                :query-params {}
                :fragment-params {}
                :path "/"
                :parameters {:query {}
                             :path {}
                             :fragment {}}})
             (rf/match-by-path router "/")))

      (is (= "/"
             (r/match->path (rf/match-by-name router ::frontpage))))

      (is (= (r/map->Match
               {:template "/foo"
                :data {:name ::foo}
                :path-params {}
                :query-params {}
                :fragment-params {}
                :path "/foo"
                :parameters {:query {}
                             :path {}
                             :fragment {}}})
             (rf/match-by-path router "/foo")))

      (is (= (r/map->Match
               {:template "/foo"
                :data {:name ::foo}
                :path-params {}
                :query-params {:mode ["foo", "bar"]}
                :fragment-params {}
                :path "/foo"
                :parameters {:query {:mode ["foo", "bar"]}
                             :path {}
                             :fragment {}}})
             (rf/match-by-path router "/foo?mode=foo&mode=bar")))

      (is (= "/foo"
             (r/match->path (rf/match-by-name router ::foo))))

      (testing "console warning about missing route"
        (is (= [{:type :warn
                 :message ["missing route" ::asd]}]
               (:messages
                 (capture-console
                   (fn []
                     (rf/match-by-name! router ::asd)))))))))

  (testing "schema coercion"
    (let [router (r/router ["/"
                            [":id" {:name ::foo
                                    :parameters {:path {:id s/Int}
                                                 :query {(s/optional-key :mode) s/Keyword}
                                                 :fragment {(s/optional-key :access_token) s/Str
                                                            (s/optional-key :refresh_token) s/Str
                                                            (s/optional-key :expires_in) s/Int
                                                            (s/optional-key :provider_token) s/Str
                                                            (s/optional-key :token_type) s/Str}}}]]
                           {:compile rc/compile-request-coercers
                            :data {:coercion rsc/coercion}})]

      (is (= (r/map->Match
               {:template "/:id"
                :path-params {:id "5"}
                :query-params {}
                :fragment-params {}
                :path "/5"
                :parameters {:query {}
                             :path {:id 5}
                             :fragment {}}})
             (m (rf/match-by-path router "/5"))))

      (is (= "/5"
             (r/match->path (rf/match-by-name router ::foo {:id 5}))))

      (testing "coercion error"
        (testing "throws without options"
          (is (thrown? js/Error (m (rf/match-by-path router "/a")))))

        (testing "thows and calles on-coercion-error"
          (let [exception (atom nil)
                match (atom nil)]
            (is (thrown? js/Error (m (rf/match-by-path router "/a" {:on-coercion-error (fn [m e]
                                                                                         (reset! match m)
                                                                                         (reset! exception e))}))))
            (is (= {:id "a"} (-> @match :path-params)))
            (is (= {:id "a"} (-> @exception (ex-data) :value))))))

      (testing "query param is read"
        (is (= (r/map->Match
                 {:template "/:id"
                  :path-params {:id "5"}
                  :query-params {:mode "foo"}
                  :fragment-params {}
                  :path "/5"
                  :parameters {:path {:id 5}
                               :query {:mode :foo}
                               :fragment {}}})
               (m (rf/match-by-path router "/5?mode=foo"))))

        (is (= "/5?mode=foo"
               (r/match->path (rf/match-by-name router ::foo {:id 5}) {:mode :foo}))))

      (testing "fragment is read"
        (is (= (r/map->Match
                 {:template "/:id"
                  :path-params {:id "5"}
                  :query-params {:mode "foo"}
                  :fragment-params {:access_token "foo"
                                    :refresh_token "bar"
                                    :provider_token "baz"
                                    :token_type "bearer"
                                    :expires_in "3600"}
                  :path "/5"
                  :parameters {:path {:id 5}
                               :query {:mode :foo}
                               :fragment {:access_token "foo"
                                          :refresh_token "bar"
                                          :provider_token "baz"
                                          :token_type "bearer"
                                          :expires_in 3600}}})
               (m (rf/match-by-path router "/5?mode=foo#access_token=foo&refresh_token=bar&provider_token=baz&token_type=bearer&expires_in=3600")))))

      (testing "console warning about missing params"
        (is (= [{:type :warn
                 :message ["missing path-params for route" ::foo
                           {:template "/:id"
                            :missing #{:id}
                            :required #{:id}
                            :path-params {}}]}]
               (:messages
                 (capture-console
                   (fn []
                     (rf/match-by-name! router ::foo {}))))))))))
