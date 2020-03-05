(ns reitit.static-perf-test
  (:require [immutant.web :as web]
            [reitit.ring :as ring]
            [reitit.ring.mime :as reitit-mime]
            [clojure.java.io :as io]
            [criterium.core :as cc]
            [ring.util.response]
            [ring.middleware.defaults]
            [ring.middleware.resource]
            [ring.util.mime-type]))

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

(def app1
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200, :body "pong"})]
       ["/files/*" (ring/create-resource-handler)]])
    (ring/create-default-handler)))

(def app2
  (ring/ring-handler
    (ring/router
      ["/ping" (constantly {:status 200, :body "pong"})])
    (some-fn
      (ring/create-resource-handler {:path "/files"})
      (ring/create-default-handler))))

(def wrap-resource
  (-> (constantly {:status 200, :body "pong"})
      (ring.middleware.resource/wrap-resource "public")))

(def wrap-defaults
  (-> (constantly {:status 200, :body "pong"})
      (ring.middleware.defaults/wrap-defaults ring.middleware.defaults/site-defaults)))

(comment
  (def server (web/run #'app1 {:port 3000, :dispatch? false, :server {:always-set-keep-alive false}})))

(defn bench-resources []

  ;; 134µs
  (cc/quick-bench
    (ring.util.response/resource-response "hello.json" {:root "public"}))

  ;; 144µs
  (cc/quick-bench
    (app1 {:request-method :get, :uri "/files/hello.json"}))

  ;; 144µs
  (cc/quick-bench
    (app2 {:request-method :get, :uri "/files/hello.json"}))

  ;; 143µs
  (cc/quick-bench
    (wrap-resource {:request-method :get, :uri "/hello.json"}))

  ;; 163µs
  (cc/quick-bench
    (wrap-defaults {:request-method :get, :uri "/hello.json"})))

(defn bench-handler []

  ;; 140ns
  (cc/quick-bench
    (app1 {:request-method :get, :uri "/ping"}))

  ;; 134ns
  (cc/quick-bench
    (app2 {:request-method :get, :uri "/ping"}))

  ;; 108µs
  (cc/quick-bench
    (wrap-resource {:request-method :get, :uri "/ping"}))

  ;; 146µs
  (cc/quick-bench
    (wrap-defaults {:request-method :get, :uri "/ping"})))

(comment
  (bench-resources)
  (bench-handler)

  (let [file (-> "logback.xml" io/resource io/file)
        name (.getName file)]

    ;; 639ns
    (cc/quick-bench
      (ring.util.mime-type/ext-mime-type name))

    ;; 106ns
    (cc/quick-bench
      (reitit-mime/ext-mime-type name reitit-mime/default-mime-types))))
