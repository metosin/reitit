(ns reitit.perf-test
  (:require [criterium.core :as cc]
            [reitit.core :as reitit]

            [bidi.bidi :as bidi]
            [compojure.api.core :refer [routes GET]]
            [ataraxy.core :as ataraxy]

            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.route.map-tree :as map-tree]
            [io.pedestal.http.route.router :as pedestal]))

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

(defn raw-title [color s]
  (println (str color (apply str (repeat (count s) "#")) "\u001B[0m"))
  (println (str color s "\u001B[0m"))
  (println (str color (apply str (repeat (count s) "#")) "\u001B[0m")))

(def title (partial raw-title "\u001B[35m"))
(def suite (partial raw-title "\u001B[32m"))

(def bidi-routes
  ["/" [["auth/login" :auth/login]
        [["auth/recovery/token/" :token] :auth/recovery]
        ["workspace/" [[[:project "/" :page] :workspace/page]]]]])

(def compojure-api-routes
  (routes
    (GET "/auth/login" [] (constantly ""))
    (GET "/auth/recovery/token/:token" [] (constantly ""))
    (GET "/workspace/:project/:page" [] (constantly ""))))

(def ataraxy-routes
  (ataraxy/compile
    '{["/auth/login"] [:auth/login]
      ["/auth/recovery/token/" token] [:auth/recovery token]
      ["/workspace/" project "/" token] [:workspace/page project token]}))

(def pedestal-routes
  (map-tree/router
    (table/table-routes
      [["/auth/login" :get (constantly "") :route-name :auth/login]
       ["/auth/recovery/token/:token" :get (constantly "") :route-name :auth/recovery]
       ["/workspace/:project/:page" :get (constantly "") :route-name :workspace/page]])))

(def reitit-routes
  (reitit/router
    [["/auth/login" :auth/login]
     ["/auth/recovery/token/:token" :auth/recovery]
     ["/workspace/:project/:page" :workspace/page]]))

(defn routing-test []

  (suite "simple routing")

  ;; 15.4µs
  (title "bidi")
  (let [call #(bidi/match-route bidi-routes "/workspace/1/1")]
    (assert (call))
    (cc/quick-bench
      (call)))

  ;; 2.9µs (-81%)
  (title "ataraxy")
  (let [call #(ataraxy/matches ataraxy-routes {:uri "/workspace/1/1"})]
    (assert (call))
    (cc/quick-bench
      (call)))

  ;; 2.4µs (-84%)
  (title "pedestal - map-tree => prefix-tree")
  (let [call #(pedestal/find-route pedestal-routes {:path-info "/workspace/1/1" :request-method :get})]
    (assert (call))
    (cc/quick-bench
      (call)))

  ;; 3.8µs (-75%)
  (title "compojure-api")
  (let [call #(compojure-api-routes {:uri "/workspace/1/1", :request-method :get})]
    (assert (call))
    (cc/quick-bench
      (call)))

  ;; 1.0µs (-94%)
  (title "reitit")
  (let [call #(reitit/match-route reitit-routes "/workspace/1/1")]
    (assert (call))
    (cc/quick-bench
      (call))))

(comment
  (routing-test))
