(ns reitit.bide-perf-test
  (:require [criterium.core :as cc]
            [reitit.core :as reitit]
            [reitit.perf-utils :refer :all]

            [bidi.bidi :as bidi]
            [ataraxy.core :as ataraxy]
            [compojure.core :as compojure]

            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.route.map-tree :as map-tree]
            [io.pedestal.http.route.router :as pedestal]
            [io.pedestal.http.route :as route]
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

(def bidi-routes
  ["/" [["auth/login" :auth/login]
        [["auth/recovery/token/" :token] :auth/recovery]
        ["workspace/" [[[:project "/" :page] :workspace/page]]]]])

(def compojure-routes
  (compojure/routes
    (compojure/GET "/auth/login" [] (constantly ""))
    (compojure/GET "/auth/recovery/token/:token" [] (constantly ""))
    (compojure/GET "/workspace/:project/:page" [] (constantly ""))))

(def ataraxy-routes
  (ataraxy/compile
    '{["/auth/login"] [:auth/login]
      ["/auth/recovery/token/" token] [:auth/recovery token]
      ["/workspace/" project "/" token] [:workspace/page project token]}))

(def pedestal-routes
  (table/table-routes
    [["/auth/login" :get (constantly "") :route-name :auth/login]
     ["/auth/recovery/token/:token" :get (constantly "") :route-name :auth/recovery]
     ["/workspace/:project/:page" :get (constantly "") :route-name :workspace/page]]))

(def pedestal-router
  (map-tree/router
    pedestal-routes))

(def pedestal-url-for (route/url-for-routes pedestal-routes))

(def reitit-routes
  (reitit/router
    [["/auth/login" :auth/login]
     ["/auth/recovery/token/:token" :auth/recovery]
     ["/workspace/:project/:page" :workspace/page]]))

(def ring-app
  (ring/ring-handler
    (ring/router
      [["/auth/login" {:get identity}]
       ["/auth/recovery/token/:token" {:get identity}]
       ["/workspace/:project/:page" {:get identity}]])))

(comment

  (ring-app {:request-method :get, :uri "/auth/login"})

  ;; 213ns
  ;; 204ns (remove if)
  ;; 163ns (inline fast-assoc)
  ;; 156ns (don't inline fast-assoc)
  ;; 128ns (single method dispatch)
  ;;  80ns --> (don't inject router & match)
  (cc/quick-bench
    (ring-app {:request-method :post, :uri "/auth/login"})))

(defn routing-test1 []

  (suite "static route")

  ;; 1600 µs
  (title "bidi")
  (assert (bidi/match-route bidi-routes "/auth/login"))
  (cc/quick-bench
    (dotimes [_ 1000]
      (bidi/match-route bidi-routes "/auth/login")))

  ;; 1400 µs
  (title "ataraxy")
  (let [request {:uri "/auth/login"}]
    (assert (ataraxy/matches ataraxy-routes request))
    (cc/quick-bench
      (dotimes [_ 1000]
        (ataraxy/matches ataraxy-routes request))))

  ;; 1000 µs
  (title "pedestal - map-tree => prefix-tree")
  (let [request {:path-info "/auth/login" :request-method :get}]
    (assert (pedestal/find-route pedestal-router {:path-info "/auth/login" :request-method :get}))
    (cc/quick-bench
      (dotimes [_ 1000]
        (pedestal/find-route pedestal-router {:path-info "/auth/login" :request-method :get}))))

  ;; 1400 µs
  (title "compojure")
  (let [request {:uri "/auth/login", :request-method :get}]
    (assert (compojure-routes request))
    (cc/quick-bench
      (dotimes [_ 1000]
        (compojure-routes request))))

  ;; 3.2 µs (300-500x)
  (title "reitit")
  (assert (reitit/match-by-path reitit-routes "/auth/login"))
  (cc/quick-bench
    (dotimes [_ 1000]
      (reitit/match-by-path reitit-routes "/auth/login"))))

(defn routing-test2 []

  (suite "wildcard route")

  ;; 12800 µs
  (title "bidi")
  (assert (bidi/match-route bidi-routes "/workspace/1/1"))
  (cc/quick-bench
    (dotimes [_ 1000]
      (bidi/match-route bidi-routes "/workspace/1/1")))

  ;; 2800 µs
  (title "ataraxy")
  (let [request {:uri "/workspace/1/1"}]
    (assert (ataraxy/matches ataraxy-routes request))
    (cc/quick-bench
      (dotimes [_ 1000]
        (ataraxy/matches ataraxy-routes request))))

  ;; 2100 µs
  (title "pedestal")
  (let [request {:path-info "/workspace/1/1" :request-method :get}]
    (assert (pedestal/find-route pedestal-router request))
    (cc/quick-bench
      (dotimes [_ 1000]
        (pedestal/find-route pedestal-router request))))

  ;; 3400 µs
  (title "compojure")
  (let [request {:uri "/workspace/1/1", :request-method :get}]
    (assert (compojure-routes request))
    (cc/quick-bench
      (dotimes [_ 1000]
        (compojure-routes request))))

  ;; 710 µs (3-18x)
  ;; 530 µs (4-24x) -25% prefix-tree-router
  ;; 710 µs (3-18x) segment-router
  (title "reitit")
  (assert (reitit/match-by-path reitit-routes "/workspace/1/1"))
  (cc/quick-bench
    (dotimes [_ 1000]
      (reitit/match-by-path reitit-routes "/workspace/1/1"))))

(defn reverse-routing-test []

  (suite "reverse routing")

  ;; 2.0µs (-59%)
  (title "bidi")
  (let [call #(bidi/path-for bidi-routes :workspace/page :project "1" :page "1")]
    (assert (= "/workspace/1/1" (call)))
    (cc/quick-bench
      (call)))

  (title "ataraxy doesn't support reverse routing :(")

  ;; 3.8µs (-22%)
  (title "pedestal - map-tree => prefix-tree")
  (let [call #(pedestal-url-for :workspace/page :path-params {:project "1" :page "1"})]
    (assert (= "/workspace/1/1" (call)))
    (cc/quick-bench
      (call)))

  ;; 4.9µs
  (title "compojure-api")

  ;; 850ns (-83%)
  (title "reitit")
  (let [call #(reitit/match-by-name reitit-routes :workspace/page {:project "1", :page "1"})]
    (assert (call))
    (cc/quick-bench
      (call))))

(comment
  (routing-test1)
  (routing-test2)
  (reverse-routing-test))
