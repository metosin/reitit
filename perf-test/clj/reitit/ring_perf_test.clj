(ns reitit.ring-perf-test
  (:require [criterium.core :as cc]
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

(defn create-app [options]
  (ring/ring-handler
    (ring/router
      [["/auth/login" identity]
       ["/auth/recovery/token/:token" identity]
       ["/workspace/:project/:page" identity]])
    (ring/create-default-handler)
    options))

(defn bench-app []
  (let [request {:request-method :post, :uri "/auth/login"}
        app1 (create-app nil)
        app2 (create-app {:inject-match? false, :inject-router? false})]
    ;; 192ns (initial)
    ;; 163ns (always assoc path params)
    ;; 132ns (expand methods)
    ;; 111ns (java-segment-router)
    (cc/quick-bench
      (app1 request))

    ;; 113ns (don't inject router)
    ;;  89ns (don't inject router & match)
    ;;  77ns (java-segment-router)
    (cc/quick-bench
      (app2 request))))

(comment
  (bench-app))
