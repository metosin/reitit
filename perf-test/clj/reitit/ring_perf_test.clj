(ns reitit.ring-perf-test
  (:require [criterium.core :as cc]
            [reitit.perf-utils :refer :all]
            [reitit.ring :as ring]))

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

(def app
  (ring/ring-handler
    (ring/router
      [["/auth/login" identity]
       ["/auth/recovery/token/:token" identity]
       ["/workspace/:project/:page" identity]])))

(comment
  (let [request {:request-method :post, :uri "/auth/login"}]
    ;; 192ns (initial)
    ;; 163ns (always assoc path params)
    ;; 132ns (expand methods)
    (cc/quick-bench
      (app request))

    ;; 113ns (don't inject router)
    ;;  89ns (don't inject router & match)
    ))
