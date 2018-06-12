(ns reitit.frontend.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.coercion :as coercion]
            [schema.core :as s]
            [reitit.coercion.schema :as schema-coercion]))

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
      (is (= (r/map->Match
               {:template "/foo"
                :data {:name ::foo}
                :path-params {}
                :path "/foo"
                :parameters {:query {}
                             :path {}}})
             (rf/match-by-path router "/foo")))))

  (testing "schema coercion"
    (let [router (r/router ["/"
                            [":id" {:name ::foo
                                    :parameters {:path {:id s/Int}
                                                 :query {(s/optional-key :mode) s/Keyword}}}]]
                           {:compile coercion/compile-request-coercers
                            :data {:coercion schema-coercion/coercion}})]
      (is (= (r/map->Match
               {:template "/:id"
                :path-params {:id "5"}
                :path "/5"
                :parameters {:query {}
                             :path {:id 5}}})
             (assoc (rf/match-by-path router "/5") :data nil :result nil)))
      (is (= (r/map->Match
               {:template "/:id"
                :path-params {:id "5"}
                ;; Note: query not included in path
                :path "/5"
                :parameters {:path {:id 5}
                             :query {:mode :foo}}})
             (assoc (rf/match-by-path router "/5?mode=foo") :data nil :result nil))))))
