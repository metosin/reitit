(ns reitit.json-perf
  (:require [criterium.core :as cc]
            [reitit.perf-utils :refer :all]

    ;; reitit
            [reitit.ring :as ring]
            [muuntaja.middleware :as mm]

    ;; bidi-yada
            [yada.yada :as yada]
            [bidi.ring :as bidi-ring]
            [byte-streams :as bs]

    ;; defaults
            [ring.middleware.defaults :as defaults]
            [compojure.core :as compojure]
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

;; TODO: naive implementation
(defn- with-security-headers [response]
  (update
    response
    :headers
    (fn [headers]
      (-> headers
          (assoc "x-frame-options" "SAMEORIGIN")
          (assoc "x-xss-protection" "1; mode=block")
          (assoc "x-content-type-options" "nosniff")))))

(def security-middleware
  {:name ::security
   :wrap (fn [handler]
           (fn [request]
             (with-security-headers (handler request))))})

(def reitit-app
  (ring/ring-handler
    (ring/router
      ["/api/ping"
       {:get {:handler (fn [_] {:status 200, :body {:ping "pong"}})}}]
      {:data {:middleware [mm/wrap-format
                           security-middleware]}})))

(def bidi-yada-app
  (bidi-ring/make-handler
    ["/api/ping"
     (yada/resource
       {:produces {:media-type "application/json"}
        :methods {:get {:response (fn [_] {:ping "pong"})}}})]))

(def defaults-app
  (defaults/wrap-defaults
    (mm/wrap-format
      (compojure/GET "/api/ping" [] {:status 200, :body {:ping "pong"}}))
    defaults/site-defaults))

(def request {:request-method :get, :uri "/api/ping"})

(comment
  (defaults-app request)
  @(bidi-yada-app request)
  (reitit-app request))

(comment
  (slurp (:body (defaults-app request)))
  (slurp (:body (reitit-app request)))
  (bs/to-string (:body @(bidi-yada-app request))))


(defn expect! [body]
  (assert (str/starts-with? body "{\"ping\":\"pong\"}")))

(defn perf-test []

  ;; 176µs
  (title "compojure + ring-defaults")
  (let [f (fn [] (defaults-app request))]
    (expect! (-> (f) :body slurp))
    (cc/quick-bench (f)))

  ;; 60µs
  (title "bidi + yada")
  (let [f (fn [] (bidi-yada-app request))]
    (expect! (-> (f) deref :body bs/to-string))
    (cc/quick-bench (f)))

  ;; 5.0µs
  (title "reitit-ring")
  (let [f (fn [] (reitit-app request))]
    (expect! (-> (f) :body slurp))
    (cc/quick-bench (f))))

(comment
  (perf-test))
