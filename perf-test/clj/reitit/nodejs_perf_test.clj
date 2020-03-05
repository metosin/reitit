(ns reitit.nodejs-perf-test
  (:require [reitit.perf-utils :refer [title]]
            [immutant.web :as web]
            [reitit.ring :as ring]))

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

(defn h [name req]
  (let [id (-> req :path-params :id)]
    {:status 200, :body (str "Got " name " id " id)}))


(def app
  (ring/ring-handler
    (ring/router
      (for [name ["product" "a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "twenty"]]
        [(str "/" name "/:id") {:get (partial h name)}]))))

(for [name ["product" "a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "twenty"]]
  [(str "/" name "/:id") {:get (partial h name)}])

(app {:request-method :get, :uri "/product/foo"})

(defn routing-test []

  ;; 21385 / 14337
  "barista"

  ;; 26259 / 25571
  "choreographer"

  ;; 24277 / 19174
  "clutch"

  ;; 26158 / 25584
  "connect"

  ;; 24614 / 25413
  "escort"

  ;; 21979 / 18595
  "express"

  ;; 23123 / 25405
  "find-my-way"

  ;; 24798 / 25286
  "http-hash"

  ;; 24215 / 23670
  "i40"

  ;; 23561 / 26278
  "light-router"

  ;; 28362 / 30056
  "http-raw"

  ;; 25310 / 25126
  "regex"

  ;; 112719 / 113959
  (title "reitit")
  ;; wrk -d ${DURATION:="30s"} http://127.0.0.1:2048/product/foo
  ;; wrk -d ${DURATION:="30s"} http://127.0.0.1:2048/twenty/bar
  (assert (= {:status 200, :body "Got product id foo"} (app {:request-method :get, :uri "/product/foo"})))
  (assert (= {:status 200, :body "Got twenty id bar"} (app {:request-method :get, :uri "/twenty/bar"}))))

(comment
  (web/run app {:port 2048, :dispatch? false, :server {:always-set-keep-alive false}})
  (routing-test))

(comment
  (require '[compojure.core :as c])
  (def app (apply
             c/routes
             (for [name ["product" "a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "twenty"]]
               (eval `(c/GET ~(str "/" name "/:id") [~'id] (str "Got " ~name " id " ~'id))))))

  (require '[ring.adapter.jetty :as jetty])
  ;; 57862 / 54290
  ;; wrk -d ${DURATION:="30s"} http://127.0.0.1:8080/product/foo
  ;; wrk -d ${DURATION:="30s"} http://127.0.0.1:8080/twenty/bar
  (jetty/run-jetty app {:port 8080}))
