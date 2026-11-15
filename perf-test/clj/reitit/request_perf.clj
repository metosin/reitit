(ns reitit.request-perf
  (:require [criterium.core :as cc]
            [reitit.perf-utils :refer [title]]
            [potemkin :as p]))

(set! *warn-on-reflection* true)

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

(defprotocol RawRequest
  (-uri [this])
  (-request-method [this])
  (-path-params [this]))

(p/def-derived-map ZeroCopyRequest
  [raw]
  :uri (-uri raw)
  :request-method (-request-method raw)
  :path-params (-path-params raw))

(defprotocol RingRequest
  (get-uri [this])
  (get-request-method [this])
  (get-path-params [this]))

(defn ring-request [raw]
  {:uri (-uri raw)
   :request-method (-request-method raw)
   :path-params (-path-params raw)})

(defrecord RecordRequest [uri request-method path-params])

(defn record-request [raw]
  (->RecordRequest (-uri raw) (-request-method raw) (-path-params raw)))

(defrecord RawRingRequest [raw]
  RingRequest
  (get-uri [_] (-uri raw))
  (get-request-method [_] (-request-method raw))
  (get-path-params [_] (-path-params raw)))

(def raw
  (reify
    RawRequest
    (-uri [_] "/ping")
    (-request-method [_] :get)
    (-path-params [_] {:a 1})))

(defn bench-all! []

  ;; 530ns
  (title "potemkin zero-copy")
  (assert (= :get (:request-method (->ZeroCopyRequest raw))))
  (cc/quick-bench
    (let [req (->ZeroCopyRequest raw)]
      (dotimes [_ 10]
        (:request-method req))))

  ;; 73ns
  (title "map copy-request")
  (assert (= :get (:request-method (ring-request raw))))
  (cc/quick-bench
    (let [req (ring-request raw)]
      (dotimes [_ 10]
        (:request-method req))))

  ;; 7ns
  (title "record copy-request")
  (assert (= :get (:request-method (record-request raw))))
  (cc/quick-bench
    (let [req (record-request raw)]
      (dotimes [_ 10]
        (:request-method req))))

  ;; 7ns
  (title "request protocols")
  (assert (= :get (get-request-method (->RawRingRequest raw))))
  (cc/quick-bench
    (let [req (->RawRingRequest raw)]
      (dotimes [_ 10]
        (get-request-method req)))))

(comment
  (bench-all!))
