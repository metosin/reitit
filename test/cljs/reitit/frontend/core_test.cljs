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
                :path "/"
                :parameters {:query {}
                             :path {}}})
             (rf/match-by-path router "/")))

      (is (= "/"
             (r/match->path (rf/match-by-name router ::frontpage))))

      (is (= (r/map->Match
               {:template "/foo"
                :data {:name ::foo}
                :path-params {}
                :path "/foo"
                :parameters {:query {}
                             :path {}}})
             (rf/match-by-path router "/foo")))

      (is (= "/foo"
             (r/match->path (rf/match-by-name router ::foo))))

      (is (= [{:type :warn
               :message ["missing route" ::asd]}]
             (:messages
               (capture-console
                 (fn []
                   (rf/match-by-name! router ::asd))))))))

  (testing "schema coercion"
    (let [router (r/router ["/"
                            [":id" {:name ::foo
                                    :parameters {:path {:id s/Int}
                                                 :query {(s/optional-key :mode) s/Keyword}}}]]
                           {:compile rc/compile-request-coercers
                            :data {:coercion rsc/coercion}})]
      (is (= (r/map->Match
               {:template "/:id"
                :path-params {:id "5"}
                :path "/5"
                :parameters {:query {}
                             :path {:id 5}}})
             (m (rf/match-by-path router "/5"))))

      (is (= "/5"
             (r/match->path (rf/match-by-name router ::foo {:id 5}))))

      (is (= (r/map->Match
               {:template "/:id"
                :path-params {:id "5"}
                ;; Note: query not included in path
                :path "/5"
                :parameters {:path {:id 5}
                             :query {:mode :foo}}})
             (m (rf/match-by-path router "/5?mode=foo"))))

      (is (= "/5?mode=foo"
             (r/match->path (rf/match-by-name router ::foo {:id 5}) {:mode :foo})))

      (is (= [{:type :warn
               :message ["missing path-params for route" ::foo
                         {:template "/:id"
                          :missing #{:id}
                          :required #{:id}
                          :path-params {}}]}]
             (:messages
               (capture-console
                 (fn []
                   (rf/match-by-name! router ::foo {})))))))))
