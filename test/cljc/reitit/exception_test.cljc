(ns reitit.exception-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.spec :as rs]
            [reitit.core :as r]
            [reitit.dev.pretty :as pretty]
            [clojure.spec.alpha :as s]
            [reitit.exception :as exception])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(s/def ::role #{:admin :manager})
(s/def ::roles (s/coll-of ::role :into #{}))
(s/def ::data (s/keys :req [::role ::roles]))

(deftest errors-test

  (are [exception]
    (are [error routes]
      (is (thrown-with-msg?
            ExceptionInfo
            error
            (r/router
              routes
              {:validate rs/validate
               :exception exception})))

      #"Router contains conflicting route paths"
      [["/:a/1"]
       ["/1/:a"]]

      #"Router contains conflicting route names"
      [["/kikka" ::kikka]
       ["/kukka" ::kikka]]

      #":reitit.trie/multiple-terminators"
      [["/{a}.pdf"]
       ["/{a}-pdf"]]

      #":reitit.trie/following-parameters"
      ["/{a}{b}"]

      #":reitit.trie/unclosed-brackets"
      ["/api/{ipa"]

      #"Invalid route data"
      ["/api/ipa" {::roles #{:adminz}}]

      #"Error merging route-data"
      ["/a" {:body {}}
       ["/b" {:body [:FAIL]}]])

    exception/exception
    pretty/exception))
