(ns reitit.router-creation-perf-test
  (:require [reitit.perf-utils :refer [bench! suite]]
            [reitit.core :as r]
            [clojure.string :as str])
  (:import (java.util Random)))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro113
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(defn random [^long seed]
  (Random. seed))

(defn rand-str [^Random rnd len]
  (apply str (take len (repeatedly #(char (+ (.nextInt rnd 26) 97))))))

(defn route [rnd]
  (str/join "/" (repeatedly (+ 2 (.nextInt rnd 8)) (fn [] (rand-str rnd (.nextInt rnd 10))))))

(def hundred-routes
  (let [rnd (random 1)]
    (mapv (fn [n] [(route rnd) (keyword (str "route" n))]) (range 100))))

(conj hundred-routes (last hundred-routes))


(defn bench-routers []

  (suite "non-conflicting")

  ;; 104ms
  ;;  11ms (reuse parts in conflict resolution)
  (bench! "default" (r/router hundred-routes))

  ;; 7ms
  (bench! "linear" (r/router hundred-routes {:router r/linear-router, :conflicts nil}))

  (suite "conflicting")
  (let [routes (conj hundred-routes [(first (last hundred-routes)) ::route])]

    ;; 205ms
    ;; 105ms (cache path-conflicts)
    ;;  13ms (reuse parts in conflict resolution)
    (bench! "default" (r/router routes {:conflicts nil}))))

(comment
  (bench-routers))
