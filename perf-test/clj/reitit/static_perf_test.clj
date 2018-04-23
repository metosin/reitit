(ns reitit.static-perf-test
  (:require [reitit.perf-utils :refer :all]
            [immutant.web :as web]
            [reitit.ring :as ring]
            [clojure.java.io :as io]
            [criterium.core :as cc]
            [ring.util.mime-type :as mime]))

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

(def app
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200, :body "pong"})]
       ["/files/*" (ring/create-resource-handler)]])
    (ring/create-default-handler)))

(def app
  (ring/ring-handler
    (ring/router
      ["/ping" (constantly {:status 200, :body "pong"})])
    (some-fn
      (ring/create-resource-handler {:path "/files"})
      (ring/create-default-handler))))

(comment
  (def server (web/run #'app {:port 3000, :dispatch? false, :server {:always-set-keep-alive false}}))
  (routing-test))

(comment
  (let [file (-> "logback.xml" io/resource io/file)
        name (.getName file)]

    ;; 639ns
    (cc/quick-bench
      (mime/ext-mime-type name))


    ;; 106ns
    (cc/quick-bench
      (ext-mime-type name))))
