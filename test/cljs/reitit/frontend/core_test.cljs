(ns reitit.frontend.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.coercion :as coercion]
            [schema.core :as s]
            [reitit.coercion.schema :as schema]))

(deftest match-by-path-test
  (testing "simple"
    (let [router (r/router ["/"
                            ["" ::frontpage]
                            ["foo" ::foo]
                            ["bar" ::bar]])]
      (is (= {:template "/"
              :data {:name ::frontpage}
              :path "/"}
             (rf/match-by-path router "/")))
      (is (= {:template "/foo"
              :data {:name ::foo}
              :path "/foo"}
             (rf/match-by-path router "/foo")))))

  (testing "schema coercion"
    (let [router (r/router ["/"
                            [":id"
                             {:name ::foo
                              :parameters {:path {:id s/Int}
                                           :query {(s/optional-key :mode) s/Keyword}}}]])]
      #_#_
      (is (= {:template "/5"
              :data {:name ::foo}
              :path "/5"
              :parameters {:path {:id 5}}}
             (rf/match-by-path router "/5")))
      (is (= {:template "/5?mode=foo"
              :data {:name ::foo}
              :path "/5?mode=foo"
              :parameters {:path {:id 5}
                           :query {:mode :foo}}}
             (rf/match-by-path router "/5?mode=foo"))))))
