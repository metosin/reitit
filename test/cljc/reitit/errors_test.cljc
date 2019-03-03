(ns reitit.errors-test
  (:require [reitit.spec :as rs]
            [reitit.core :as r]
            [reitit.dev.pretty :as pretty]
            [clojure.spec.alpha :as s]))

(s/def ::role #{:admin :manager})
(s/def ::roles (s/coll-of ::role :into #{}))
(s/def ::data (s/keys :req [::role ::roles]))

(comment

  ;; route conflicts
  (r/router
    [["/:a/1"]
     ["/1/:a"]]
    {:exception pretty/exception})

  ;; path conflicts
  (r/router
    [["/kikka" ::kikka]
     ["/kukka" ::kikka]]
    {:exception pretty/exception})

  ;;
  ;; trie
  ;;

  ;; two terminators
  (r/router
    [["/{a}.pdf"]
     ["/{a}-pdf"]]
    {:exception pretty/exception})

  ;; two following wilds
  (r/router
    ["/{a}{b}"]
    {:exception pretty/exception})

  ;; unclosed brackers
  (r/router
    ["/api/{ipa"]
    {:exception pretty/exception})

  ;;
  ;; spec
  ;;

  (r/router
    ["/api/ipa" {::roles #{:adminz}}]
    {:validate rs/validate
     :exception pretty/exception}))
