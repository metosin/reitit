(ns reitit.impl-perf-test
  (:require [criterium.core :as cc]
            [reitit.perf-utils :refer [suite]]
            [ring.util.codec]
            [reitit.impl :as impl])
  (:import (java.net URLDecoder URLEncoder)))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro11,3
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;


(defn test! [f input]
  (println "\u001B[33m")
  (println (pr-str input) "=>" (pr-str (f input)))
  (println "\u001B[0m")
  (cc/quick-bench (f input)))

(defn url-decode-naive [s]
  (URLDecoder/decode
    (.replace ^String s "+" "%2B")
    "UTF-8"))

(defn url-decode! []

  ;; ring

  ;; 890ns
  ;; 190ns
  ;; 90ns
  ;; 80ns

  ;; naive

  ;; 750ns
  ;; 340ns
  ;; 420ns
  ;; 200ns

  ;; reitit

  ;; 630ns (-29%)
  ;; 12ns (-94%)
  ;; 8ns (-91%)
  ;; 8ns (-90%)

  (doseq [fs ['ring.util.codec/url-decode
              'url-decode-naive
              'reitit.impl/url-decode]
          :let [f (deref (resolve fs))]]
    (suite (str fs))
    (doseq [s ["aja%20hiljaa+sillalla"
               "aja_hiljaa_sillalla"
               "1+1"
               "1"]]
      (test! f s))))

(defn url-encode-naive [^String s]
  (cond-> (.replace (URLEncoder/encode s "UTF-8") "+" "%20")
          (.contains s "+") (.replace "%2B" "+")
          (.contains s "~") (.replace "%7E" "~")
          (.contains s "=") (.replace "%3D" "=")
          (.contains s "!") (.replace "%21" "!")
          (.contains s "'") (.replace "%27" "'")
          (.contains s "(") (.replace "%28" "(")
          (.contains s ")") (.replace "%29" ")")))

(defn url-encode! []

  ;; ring

  ;; 2500ns
  ;; 610ns
  ;; 160ns
  ;; 120ns

  ;; naive

  ;; 1000ns
  ;; 440ns
  ;; 570ns
  ;; 200ns

  ;; reitit

  ;; 1400ns
  ;; 740ns
  ;; 180ns
  ;; 130ns

  (doseq [fs ['ring.util.codec/url-encode
              'url-encode-naive
              'reitit.impl/url-encode]
          :let [f (deref (resolve fs))]]
    (suite (str fs))
    (doseq [s ["aja hiljaa+sillalla"
               "aja_hiljaa_sillalla"
               "1+1"
               "1"]]
      (test! f s))))

(defn form-decode! []

  ;; ring

  ;; 280ns
  ;; 130ns
  ;; 43ns
  ;; 25ns

  ;; reitit

  ;; 270ns (-4%)
  ;; 20ns (-84%)
  ;; 47ns (+8%)
  ;; 12ns (-52%)

  (doseq [fs ['ring.util.codec/form-decode-str
              'reitit.impl/form-decode]
          :let [f (deref (resolve fs))]]
    (suite (str fs))
    (doseq [s ["%2Baja%20hiljaa+sillalla"
               "aja_hiljaa_sillalla"
               "1+1"
               "1"]]
      (test! f s))))

(defn form-encode! []

  ;; ring

  ;; 240ns
  ;; 120ns
  ;; 130ns
  ;; 31ns

  ;; reitit

  ;; 210ns
  ;; 120ns
  ;; 130ns
  ;; 30ns

  (doseq [fs ['ring.util.codec/form-encode
              'reitit.impl/form-encode]
          :let [f (deref (resolve fs))]]
    (suite (str fs))
    (doseq [s ["aja hiljaa+sillalla"
               "aja_hiljaa_sillalla"
               "1+1"
               "1"]]
      (test! f s))))

(defn url-encode-coll! []

  (suite "url-encode-coll")

  ;; 740ns
  (test "something to decode")
  (test! impl/url-decode-coll
         {:a "aja%20hiljaa+sillalla"
          :b "aja_hiljaa_sillalla"
          :c "1+1"
          :d "1"})

  ;; 124ns
  ;;  50ns (maybe-map-values)
  (test "nothing to decode")
  (test! impl/url-decode-coll
         {:a "aja+20hiljaa+sillalla"
          :b "aja_hiljaa_sillalla"
          :c "1+1"
          :d "1"}))

(comment
  (url-decode!)
  (url-encode!)
  (form-decode!)
  (form-encode!)
  (url-encode-coll!))
