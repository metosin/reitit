(ns reitit.cors-perf-test
  (:require [reitit.perf-utils :refer [b!]]
            [ring.middleware.cors :as cors]))

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
  (cors/wrap-cors
    (fn [_] {:status 200, :body "ok"})
    :access-control-allow-origin #"http://example.com"
    :access-control-allow-headers #{:accept :content-type}
    :access-control-allow-methods #{:get :put :post}))

(def cors-request
  {:request-method :options
   :uri "/"
   :headers {"origin" "http://example.com"
             "access-control-request-method" "POST"
             "access-control-request-headers" "Accept, Content-Type"}})

(defn cors-perf-test []

  ;;  0.04µs
  (b! "ring-cors: pass" (app {}))

  ;; 15.85µs
  (b! "ring-cors: preflight" (app cors-request)))

(comment
  (cors-perf-test))
