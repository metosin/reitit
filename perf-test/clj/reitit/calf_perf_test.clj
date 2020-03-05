(ns reitit.calf-perf-test
  (:require [criterium.core :as cc]
            [ring.util.codec]
            [reitit.impl]
            [reitit.ring :as ring]
            [reitit.core :as r]))

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


(defn test! [f input]
  (println "\u001B[33m")
  (println (pr-str input) "=>" (pr-str (f input)))
  (println "\u001B[0m")
  (cc/quick-bench (f input)))

(defn h11 [id type] {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body (str id ".11." type)})
(defn h12 [id type] {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body (str id ".12." type)})
(defn h1x [] {:status 405
              :headers {"Allow" "GET, PUT"
                        "Content-Type" "text/plain"}
              :body "405 Method not supported. Supported methods are: GET, PUT"})

(defn h21 [id] {:status 200
                :headers {"Content-Type" "text/plain"}
                :body (str id ".21")})
(defn h22 [id] {:status 200
                :headers {"Content-Type" "text/plain"}
                :body (str id ".22")})
(defn h2x [] {:status 405
              :headers {"Allow" "GET, PUT"
                        "Content-Type" "text/plain"}
              :body "405 Method not supported. Supported methods are: GET, PUT"})
(defn h30 [cid did] {:status 200
                     :headers {"Content-Type" "text/plain"}
                     :body (str cid ".3." did)})
(defn h3x [] {:status 405
              :headers {"Allow" "PUT"
                        "Content-Type" "text/plain"}
              :body "405 Method not supported. Only PUT is supported."})
(defn h40 [] {:status 200
              :headers {"Content-Type" "text/plain"}
              :body "4"})
(defn h4x [] {:status 405
              :headers {"Allow" "PUT"
                        "Content-Type" "text/plain"}
              :body "405 Method not supported. Only PUT is supported."})
(defn hxx [] {:status 400
              :headers {"Content-Type" "text/plain"}
              :body "400 Bad request. URI does not match any available uri-template."})

(def handler-reitit
  (ring/ring-handler
    (ring/router
      [["/user/:id/profile/:type/" {:get (fn [{{:keys [id type]} :path-params}] (h11 id type))
                                    :put (fn [{{:keys [id type]} :path-params}] (h12 id type))
                                    :handler (fn [_] (h1x))}]
       ["/user/:id/permissions/" {:get (fn [{{:keys [id]} :path-params}] (h21 id))
                                  :put (fn [{{:keys [id]} :path-params}] (h22 id))
                                  :handler (fn [_] (h2x))}]
       ["/company/:cid/dept/:did/" {:put (fn [{{:keys [cid did]} :path-params}] (h30 cid did))
                                    :handler (fn [_] (h3x))}]
       ["/this/is/a/static/route" {:put (fn [_] (h40))
                                   :handler (fn [_] (h4x))}]])
    (fn [_] (hxx))
    {:inject-match? false, :inject-router? false}))

(comment
  (let [request {:request-method :get, :uri "/user/1234/profile/compact/"}]
    ;; 1338ns (old)
    ;;  981ns (new)
    ;;  805ns (java)
    ;;  704ns (no-inject)
    ;;  458ns (trie)
    (cc/quick-bench
      (handler-reitit request))
    (handler-reitit request)))

(comment
  ;; 190ns
  (let [router (r/router [["/user/:id/profile/:type" ::1]
                          ["/user/:id/permissions" ::2]
                          ["/company/:cid/dept/:did" ::3]
                          ["/this/is/a/static/route" ::4]])]
    (cc/quick-bench
      (r/match-by-path router "/user/1234/profile/compact"))
    (r/match-by-path router "/user/1234/profile/compact")))

