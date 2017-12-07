(ns reitit.go-perf-test
  (:require [criterium.core :as cc]
            [reitit.perf-utils :refer :all]
            [reitit.ring :as ring]
            [clojure.string :as str]))

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

(defn h [path]
  (fn [req]
    {:status 200, :body path}))

(defn add [handler routes route]
  (let [method (-> route keys first str/lower-case keyword)
        path (-> route vals first)
        h (handler path)]
    (if (some (partial = path) (map first routes))
      (mapv (fn [[p d]] (if (= path p) [p (assoc d method h)] [p d])) routes)
      (conj routes [path {method h}]))))

(def routes [{"POST", "/1/classes/:className"},
             {"GET", "/1/classes/:className/:objectId"},
             {"PUT", "/1/classes/:className/:objectId"},
             {"GET", "/1/classes/:className"},
             {"DELETE", "/1/classes/:className/:objectId"},

             ;; Users
             {"POST", "/1/users"},
             {"GET", "/1/login"},
             {"GET", "/1/users/:objectId"},
             {"PUT", "/1/users/:objectId"},
             {"GET", "/1/users"},
             {"DELETE", "/1/users/:objectId"},
             {"POST", "/1/requestPasswordReset"},

             ;; Roles
             {"POST", "/1/roles"},
             {"GET", "/1/roles/:objectId"},
             {"PUT", "/1/roles/:objectId"},
             {"GET", "/1/roles"},
             {"DELETE", "/1/roles/:objectId"},

             ;; Files
             {"POST", "/1/files/:fileName"},

             ;; Analytics
             {"POST", "/1/events/:eventName"},

             ;; Push Notifications
             {"POST", "/1/push"},

             ;; Installations
             {"POST", "/1/installations"},
             {"GET", "/1/installations/:objectId"},
             {"PUT", "/1/installations/:objectId"},
             {"GET", "/1/installations"},
             {"DELETE", "/1/installations/:objectId"},

             ;; Cloud Functions
             {"POST", "/1/functions"}])


(def app
  (ring/ring-handler
    (ring/router
      (reduce (partial add h) [] routes))))

(defn routing-test []
  ;; https://github.com/julienschmidt/go-http-routing-benchmark
  ;; coudn't run the GO tests, so reusing just the numbers (run on better hw?):
  ;;
  ;; Intel Core i5-2500K (4x 3,30GHz + Turbo Boost), CPU-governor: performance
  ;; 2x 4 GiB DDR3-1333 RAM, dual-channel
  ;; go version go1.3rc1 linux/amd64
  ;; Ubuntu 14.04 amd64 (Linux Kernel 3.13.0-29), fresh installation

  ;;  37ns (2.0x)
  ;; 180ns (4.0x)
  ;; 200ns (4.8x)
  "httpRouter"

  ;;  77ns
  ;; 730ns
  ;; 960ns
  (title "reitit-ring")
  (let [r1 (map->Request {:request-method :get, :uri "/1/users"})
        r2 (map->Request {:request-method :get, :uri "/1/classes/go"})
        r3 (map->Request {:request-method :get, :uri "/1/classes/go/123456789"})]
    (assert (= {:status 200, :body "/1/users"} (app r1)))
    (assert (= {:status 200, :body "/1/classes/:className"} (app r2)))
    (assert (= {:status 200, :body "/1/classes/:className/:objectId"} (app r3)))
    (cc/quick-bench (app r1))
    (cc/quick-bench (app r2))
    (cc/quick-bench (app r3))))

(comment
  (routing-test))
