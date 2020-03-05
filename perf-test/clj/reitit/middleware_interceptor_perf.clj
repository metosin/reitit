(ns reitit.middleware-interceptor-perf
  (:require [criterium.core :as cc]
            [reitit.perf-utils :refer [title]]
            [reitit.middleware :as middleware]
            [reitit.interceptor :as interceptor]

            reitit.chain
            sieppari.queue
            sieppari.core
            io.pedestal.interceptor
            io.pedestal.interceptor.chain))

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

;;
;; middleware
;;

(set! *warn-on-reflection* true)

(defrecord RequestOrContext [values queue stack])

(def +items+ 10)

(defn expected! [x]
  (println x)
  #_(assert (= (range +items+) (:values x))))

(defn middleware [handler value]
  (fn [request]
    (handler request)))

(def map-request {})
(def record-request (map->RequestOrContext map-request))

(defn middleware-test []
  (let [mw (map (fn [value] [middleware value]) (range +items+))
        app (middleware/chain mw identity)]

    ;; 1000ns
    ;;   18ns (identity)
    (title "middleware - map")
    (expected! (app map-request))
    (cc/quick-bench
      (app map-request))

    ;;  365ns
    ;;   21ns (identity)
    (title "middleware - record")
    (expected! (app record-request))
    (cc/quick-bench
      (app record-request))

    ;;  6900ns
    ;; 10000ns (identity)
    (title "middleware - dynamic")
    (expected! ((middleware/chain mw identity) record-request))
    (cc/quick-bench
      ((middleware/chain mw identity) record-request))))

(defn sieppari-test []
  (let [interceptors (conj
                       (mapv
                         (fn [value]
                           {:enter identity})
                         (range +items+))
                       identity)
        queue (sieppari.queue/into-queue interceptors)
        app (fn [req] (sieppari.core/execute interceptors req))
        app2 (fn [req] (sieppari.core/execute queue req))]

    ;; 5500ns
    ;; 4000ns (identity)
    (title "sieppari - map")
    (expected! (app map-request))
    (cc/quick-bench
      (app map-request))

    ;; 4600ns
    ;; 3800ns (identity)
    (title "sieppari - record")
    (expected! (app record-request))
    (cc/quick-bench
      (app record-request))

    ;; 2200ns
    ;; 1300ns (identity)
    (title "sieppari - map (compiled queue)")
    (expected! (app2 map-request))
    (cc/quick-bench
      (app2 map-request))

    ;; 1600ns
    ;; 1300ns (identity)
    (title "sieppari - record (compiled queue)")
    (expected! (app2 record-request))
    (cc/quick-bench
      (app2 record-request))))

;;
;; Reduce
;;

(defn test-reduce []
  (let [ints (vec (range +items+))
        size (count ints)]

    ;; 64µs
    (cc/quick-bench
      (reduce #(+ ^int %1 ^int %2) ints))

    ;; 123µs
    (cc/quick-bench
      (loop [sum 0, i 0]
        (if (= i size)
          sum
          (recur (+ sum ^int (nth ints i)) (inc i)))))

    ;; 34µs
    (cc/quick-bench
      (let [iter (clojure.lang.RT/iter ints)]
        (loop [sum 0]
          (if (.hasNext iter)
            (recur (+ sum ^int (.next iter)))
            sum))))))

;;
;; Interceptor
;;

(defn interceptor [value]
  (fn [context]
    (let [values (or (:values context) [])]
      (assoc context :values (conj values value)))))

;;
;; Pedestal
;;

(defn pedestal-chain-text []
  (let [is (map io.pedestal.interceptor/interceptor
                (map (fn [value]
                       {:enter identity}) (range +items+)))
        ctx (io.pedestal.interceptor.chain/enqueue nil is)]

    ;; 8400ns
    ;; 7200ns (identity)
    (title "pedestal")
    (cc/quick-bench
      (io.pedestal.interceptor.chain/execute ctx))))

;;
;; Naive chain
;;

(defn execute [ctx f] (f ctx))

(defn executor-reduce [interceptors]
  (fn [ctx]
    (as-> ctx $
          (reduce execute $ (keep :enter interceptors))
          (reduce execute $ (reverse (keep :leave interceptors))))))

(defn interceptor-test []
  (let [interceptors (map (fn [value] [interceptor value]) (range +items+))
        app (executor-reduce (interceptor/chain interceptors identity {}))
        map-request {}
        record-request (map->RequestOrContext map-request)]

    ;; 1900ns
    (title "interceptors - map")
    (expected! (app map-request))
    (cc/quick-bench
      (app map-request))

    ;; 1300ns
    (title "interceptors - record")
    (expected! (app record-request))
    (cc/quick-bench
      (app record-request))))

;;
;; different reducers
;;

(defn enqueue [ctx interceptors]
  (let [queue (or (:queue ctx) clojure.lang.PersistentQueue/EMPTY)]
    (assoc ctx :queue (into queue interceptors))))

(defn queue [ctx interceptors]
  (let [queue (or (:queue ctx) clojure.lang.PersistentQueue/EMPTY)]
    (into queue interceptors)))

(defn leavel-all-queue [ctx stack]
  (let [it (clojure.lang.RT/iter stack)]
    (loop [ctx ctx]
      (if (.hasNext it)
        (if-let [leave (-> it .next :leave)]
          (recur (leave ctx))
          (recur ctx))
        ctx))))

(defn executor-queue [interceptors]
  (fn [ctx]
    (loop [queue (queue ctx interceptors)
           stack nil
           ctx ctx]
      (if-let [interceptor (peek queue)]
        (let [queue (pop queue)
              stack (conj stack interceptor)
              f (or (:enter interceptor) identity)]
          (recur queue stack (f ctx)))
        (leavel-all-queue ctx stack)))))

(defn leave-all-ctx-queue [ctx stack]
  (let [it (clojure.lang.RT/iter stack)]
    (loop [ctx ctx]
      (if (.hasNext it)
        (if-let [leave (-> it .next :leave)]
          (recur (leave ctx))
          (recur ctx))
        ctx))))

(defn executor-ctx-queue [interceptors]
  (fn [ctx]
    (loop [ctx (assoc ctx :queue (queue ctx interceptors))]
      (let [queue ^clojure.lang.PersistentQueue (:queue ctx)
            stack (:stack ctx)]
        (if-let [interceptor (peek queue)]
          (let [queue (pop queue)
                stack (conj stack interceptor)
                f (or (:enter interceptor) identity)]
            (recur (-> ctx (assoc :queue queue) (assoc :stac stack) f)))
          (leave-all-ctx-queue ctx stack))))))

(defn interceptor-chain-test []
  (let [interceptors (map (fn [value] [interceptor value]) (range +items+))
        app-reduce (executor-reduce (interceptor/chain interceptors identity {}))
        app-queue (executor-queue (interceptor/chain interceptors identity {}))
        app-ctx-queue (executor-ctx-queue (interceptor/chain interceptors identity {}))
        request {}]

    ;; 2000ns
    (title "interceptors - reduce")
    (expected! (app-reduce request))
    (cc/quick-bench
      (app-reduce request))

    ;; 2500ns
    (title "interceptors - queue")
    (expected! (app-queue request))
    (cc/quick-bench
      (app-queue request))

    ;; 3200ns
    (title "interceptors - ctx-queue")
    (expected! (app-ctx-queue request))
    (cc/quick-bench
      (app-ctx-queue request))))

(comment
  (interceptor-test)
  (middleware-test)
  (sieppari-test)
  (pedestal-chain-text)
  (interceptor-chain-test))

; Middleware (static chain) => 5µs
; Middleware (dynamic chain) => 60µs

; Interceptor (static queue) => 20µs
; Interceptor (context queues) => 30µs
; Pedestal (context queues) => 79µs
