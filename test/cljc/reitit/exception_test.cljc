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
    (are [error routes opts]
      (is (thrown-with-msg?
            ExceptionInfo
            error
            (r/router
              routes
              (merge {:exception exception} opts))))

      #"Router contains conflicting route paths"
      [["/:a/1"]
       ["/1/:a"]]
      nil

      #"Router contains conflicting route names"
      [["/kikka" ::kikka]
       ["/kukka" ::kikka]]
      nil

      #":reitit.trie/multiple-terminators"
      [["/{a}.pdf"]
       ["/{a}-pdf"]]
      nil

      #":reitit.trie/following-parameters"
      ["/{a}{b}"]
      nil

      #":reitit.trie/unclosed-brackets"
      ["/api/{ipa"]
      nil

      #"Invalid route data"
      ["/api/ipa" {::roles #{:adminz}}]
      {:validate rs/validate})

    exception/exception
    pretty/exception))
