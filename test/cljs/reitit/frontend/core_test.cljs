(ns reitit.frontend.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.coercion :as rc]
            [schema.core :as s]
            [reitit.coercion.schema :as rcs]
            [reitit.coercion.malli :as rcm]
            [reitit.frontend.test-utils :refer [capture-console]]))

(defn m [x]
  (assoc x :data nil :result nil))

(defn decode-form [s]
  ;; RFC 6749 4.2.2 specifies OAuth token response uses
  ;; form-urlencoded format to encode values in the fragment string.
  ;; Use built-in JS function to decode.
  ;; ring.util.codec/decode-form works on Clj.
  (when s
    (->> (.entries (js/URLSearchParams. s))
         (map (fn [[k v]] [(keyword k) v]))
         (into {}))))

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
                :path "/"
                :fragment nil
                :parameters {:query {}
                             :path {}
                             :fragment nil}})
             (rf/match-by-path router "/")))

      (is (= "/"
             (r/match->path (rf/match-by-name router ::frontpage))))

      (is (= (r/map->Match
               {:template "/foo"
                :data {:name ::foo}
                :path-params {}
                :query-params {}
                :path "/foo"
                :fragment nil
                :parameters {:query {}
                             :path {}
                             :fragment nil}})
             (rf/match-by-path router "/foo")))

      (is (= (r/map->Match
               {:template "/foo"
                :data {:name ::foo}
                :path-params {}
                :query-params {:mode ["foo", "bar"]}
                :path "/foo"
                :fragment nil
                :parameters {:query {:mode ["foo", "bar"]}
                             :path {}
                             :fragment nil}})
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
                                                 :fragment (s/maybe s/Str)}}]]
                           {:compile rc/compile-request-coercers
                            :data {:coercion rcs/coercion}})]

      (is (= (r/map->Match
               {:template "/:id"
                :path-params {:id "5"}
                :query-params {}
                :path "/5"
                :fragment nil
                :parameters {:query {}
                             :path {:id 5}
                             :fragment nil}})
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
                  :path "/5"
                  :fragment nil
                  :parameters {:path {:id 5}
                               :query {:mode :foo}
                               :fragment nil}})
               (m (rf/match-by-path router "/5?mode=foo"))))

        (is (= "/5?mode=foo"
               (r/match->path (rf/match-by-name router ::foo {:id 5}) {:mode :foo}))))

      (testing "fragment string is read"
        (is (= (r/map->Match
                 {:template "/:id"
                  :path-params {:id "5"}
                  :query-params {:mode "foo"}
                  :path "/5"
                  :fragment "fragment"
                  :parameters {:path {:id 5}
                               :query {:mode :foo}
                               :fragment "fragment"}})
               (m (rf/match-by-path router "/5?mode=foo#fragment")))))

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
                   (rf/match-by-name! router ::foo {})))))))))

  (testing "malli coercion"
    (let [router (r/router ["/"
                            [":id" {:name ::foo
                                    :parameters {:path [:map
                                                        [:id :int]]
                                                 :query [:map
                                                         [:mode {:optional true} :keyword]]
                                                 :fragment [:maybe
                                                            [:map
                                                             {:decode/string decode-form}
                                                             [:access_token :string]
                                                             [:refresh_token :string]
                                                             [:expires_in :int]
                                                             [:provider_token :string]
                                                             [:token_type :string]]]}}]]
                           {:compile rc/compile-request-coercers
                            :data {:coercion rcm/coercion}})]

      (is (= (r/map->Match
               {:template "/:id"
                :path-params {:id "5"}
                :query-params {}
                :path "/5"
                :fragment nil
                :parameters {:query {}
                             :path {:id 5}
                             :fragment nil}})
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
                  :path "/5"
                  :fragment nil
                  :parameters {:path {:id 5}
                               :query {:mode :foo}
                               :fragment nil}})
               (m (rf/match-by-path router "/5?mode=foo"))))

        (is (= "/5?mode=foo"
               (r/match->path (rf/match-by-name router ::foo {:id 5}) {:mode :foo}))))

      (testing "fragment string is read"
        (is (= (r/map->Match
                 {:template "/:id"
                  :path-params {:id "5"}
                  :query-params {:mode "foo"}
                  :path "/5"
                  :fragment "access_token=foo&refresh_token=bar&provider_token=baz&token_type=bearer&expires_in=3600"
                  :parameters {:path {:id 5}
                               :query {:mode :foo}
                               :fragment {:access_token "foo"
                                          :refresh_token "bar"
                                          :provider_token "baz"
                                          :token_type "bearer"
                                          :expires_in 3600}}})
               (m (rf/match-by-path router "/5?mode=foo#access_token=foo&refresh_token=bar&provider_token=baz&token_type=bearer&expires_in=3600"))))))))

(deftest update-path-query-params-test
  (is (= "foo?bar=1"
         (rf/update-path-query-params "foo" assoc :bar 1)))

  (testing "Keep fragment"
    (is (= "foo?bar=1&zzz=2#aaa"
           (rf/update-path-query-params "foo?bar=1#aaa" assoc :zzz 2))))

  (is (= "foo?asd=1&bar=1"
         (rf/update-path-query-params "foo?asd=1" assoc :bar 1)))

  (is (= "foo?bar=1"
         (rf/update-path-query-params "foo?asd=1&bar=1" dissoc :asd)))

  (is (= "foo"
         (rf/update-path-query-params "foo?asd=1" dissoc :asd)))

  (testing "Need to coerce current values manually"
    (is (= "foo?foo=2"
           (rf/update-path-query-params "foo?foo=1" update :foo #(inc (js/parseInt %)))))))
