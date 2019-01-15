(ns reitit.segment-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.segment :as s]))

(-> (s/insert nil "/foo" {:a 1}) (s/compile) (s/lookup "/foo"))
; => #reitit.segment.Match{:data {:a 1}, :path-params {}}

(-> (s/insert nil "/foo" {:a 1}) (s/insert "/foo/*" {:b 1}) (s/compile) (s/lookup "/foo"))
; => nil

(-> (s/insert nil "/foo" {:a 1}) (s/insert "/foo/*" {:b 1}) (s/compile) (s/lookup "/foo/bar"))
; => #reitit.segment.Match{:data {:b 1}, :path-params {: "bar"}}
