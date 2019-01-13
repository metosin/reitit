(ns reitit.calf-perf-test
  (:require [criterium.core :as cc]
            [reitit.perf-utils :refer :all]
            [ring.util.codec]
            [reitit.impl]
            [reitit.segment :as segment]
            [reitit.impl :as impl]
            [reitit.ring :as ring]
            [reitit.core :as r])
  (:import (reitit SegmentTrie Trie$Matcher)
           (calfpath Util)))

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
  (do
    (println "\u001B[33m")
    (println (pr-str input) "=>" (pr-str (f input)))
    (println "\u001B[0m")
    (cc/quick-bench (f input))))

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
    (fn [_] (hxx))))

#_(let [request {:request-method :put
                 :uri "/this/is/a/static/route"}]
    (handler-reitit request)
    (cc/quick-bench
      (handler-reitit request)))

(let [request {:request-method :get
               :uri "/user/1234/profile/compact/"}]
  ;;  OLD: 1338ns
  ;;  NEW:  981ns
  ;; JAVA:  805ns
  ;; NO-INJECT: 704ns
  #_(cc/quick-bench
      (handler-reitit request))
  (handler-reitit request))

(comment
  (impl/segments "/user/1234/profile/compact")
  ;; 145ns
  (cc/quick-bench
    (impl/segments "/user/1234/profile/compact")))

(comment
  (SegmentTrie/split "/user/1234/profile/compact")
  ;; 91ns
  (cc/quick-bench
    (SegmentTrie/split "/user/1234/profile/compact")))

(comment
  (let [router (r/router ["/user/:id/profile/:type"])]
    (cc/quick-bench
      (r/match-by-path router "/user/1234/profile/compact"))))

(let [lookup ^Trie$Matcher (SegmentTrie/sample)]
  (SegmentTrie/lookup lookup "/user/1234/profile/compact")
  #_(cc/quick-bench
      (SegmentTrie/lookup lookup "/user/1234/profile/compact")))

(let [router (r/router [["/user/:id" ::1]
                        ["/user/:id/permissions" ::2]
                        ["/company/:cid/dept/:did" ::3]
                        ["/this/is/a/static/route" ::4]])]
  #_(cc/quick-bench
      (r/match-by-path router "/user/1234/profile/compact"))
  (r/match-by-path router "/user/1234"))

;; 281ns
(let [router (r/router [["/user/:id/profile/:type" ::1]
                        ["/user/:id/permissions" ::2]
                        ["/company/:cid/dept/:did" ::3]
                        ["/this/is/a/static/route" ::4]])]
  #_(cc/quick-bench
    (r/match-by-path router "/user/1234/profile/compact"))
  (r/match-by-path router "/user/1234/profile/compact"))

(read-string
  (str
    (.matcher
      (doto (SegmentTrie.)
        (.add "/user" 1)
        #_(.add "/user/id/permissions" 2)
        (.add "/user/id/permissions2" 3)))))

(SegmentTrie/lookup
  (.matcher
    (doto (SegmentTrie.)
      (.add "/user/1" 1)
      (.add "/user/1/permissions" 2)))
  "/user/1")

(.matcher
  (doto (SegmentTrie.)
    (.add "/user/1" 1)
    (.add "/user/1/permissions" 2)))

;; 137ns
(let [m (.matcher
          (doto (SegmentTrie.)
            (.add "/user/:id/profile/:type" 1)))]
  #_(cc/quick-bench
      (SegmentTrie/lookup m "/user/1234/profile/compact"))
  (SegmentTrie/lookup m "/user/1234/profile/compact"))

(comment

  (let [matcher ^Trie$Matcher (SegmentTrie/sample)]
    (SegmentTrie/lookup matcher "/user/1234/profile/compact")
    (cc/quick-bench
      (SegmentTrie/lookup matcher "/user/1234/profile/compact")))

  ;; 173ns
  (let [lookup ^Trie$Matcher (SegmentTrie/tree2)]
    (SegmentTrie/lookup lookup "/user/1234/profile/compact")
    (cc/quick-bench
      (SegmentTrie/lookup lookup "/user/1234/profile/compact")))


  ;; 140ns
  (let [lookup ^Trie$Matcher (SegmentTrie/tree1)]
    (SegmentTrie/lookup lookup "/user/1234/profile/compact")
    (cc/quick-bench
      (SegmentTrie/lookup lookup "/user/1234/profile/compact")))

  ;; 849ns (clojure, original)
  ;; 599ns (java, initial)
  ;; 173ns (fast split)
  (let [router (r/router ["/user/:id/profile/:type"])]
    (r/match-by-path router "/user/1234/profile/compact")
    (cc/quick-bench
      (r/match-by-path router "/user/1234/profile/compact")))

  ;; 849ns (clojure, original)
  ;; 599ns (java, initial)
  ;; 173ns (java, optimized)
  (let [router (r/router [["/user/:id/profile/:type/" ::1]
                          ["/user/:id/permissions/" ::2]
                          ["/company/:cid/dept/:did/" ::3]
                          ["/this/is/a/static/route" ::4]])]
    (cc/quick-bench
      (r/match-by-path router "/user/1234/profile/compact")))

  ;; 131ns
  (let [route ["/user/" :id "/profile/" :type "/"]]
    (cc/quick-bench
      (Util/matchURI "/user/1234/profile/compact/" route)))

  ;; 728ns
  (cc/quick-bench
    (r/match-by-path ring/ROUTER (:uri ring/REQUEST))))

(set! *warn-on-reflection* true)

(comment
  (let [request {:request-method :get
                 :uri "/user/1234/profile/compact/"}]
    (time
      (dotimes [_ 1000]
        (handler-reitit request)))))

(comment
  (Util/matchURI "/user/1234/profile/compact/" ["/user/" :id "/profile/" :type "/"])
  (cc/quick-bench
    (Util/matchURI "/user/1234/profile/compact/" ["/user/" :id "/profile/" :type "/"]))

  (cc/quick-bench
    (SegmentTrie/split "/user/1234/profile/compact/"))

  (cc/quick-bench
    (.split "/user/1234/profile/compact/" "/" 666)))

(import '[reitit Segment2])

(def paths ["kikka" "kukka" "kakka" "abba" "jabba" "1" "2" "3" "4"])
(def a (Segment2/createArray paths))
(def h (Segment2/createHash paths))

(set! *warn-on-reflection* true)

(comment
  (let [segment (segment/create
                  [["/user/:id/profile/:type/" 1]
                   ["/user/:id/permissions/" 2]
                   ["/company/:cid/dept/:did/" 3]
                   ["/this/is/a/static/route" 4]])]
    (segment/lookup segment "/user/1/profile/compat/")

    ;; OLD: 602ns
    ;; NEW: 472ns
    (cc/quick-bench
      (segment/lookup segment "/user/1/profile/compat/"))

    ;; OLD: 454ns
    ;; NEW: 372ns
    (cc/quick-bench
      (segment/lookup segment "/user/1/permissions/"))))

#_(cc/quick-bench
    (SegmentTrie/split "/user/1/profile/compat"))

#_(SegmentTrie/split "/user/1/profile/compat")

#_(cc/quick-bench
    (Segment2/hashLookup h "abba"))



(comment
  (cc/quick-bench
    (dotimes [_ 1000]
      ;; 7ns
      (Segment2/arrayLookup a "abba")))

  (cc/quick-bench
    (dotimes [_ 1000]
      ;; 3ns
      (Segment2/hashLookup h "abba"))))

(comment
  (time
    (dotimes [_ 1000]
      (Util/matchURI "/user/1234/profile/compact/" ["/user/" :id "/profile/" :type "/"])))


  (time
    (let [s (s/create [["/user/:id/profile/:type/" 1]])]
      (dotimes [_ 1000]
        (s/lookup s "/user/1234/profile/compact/"))))

  (let [m {"/abba" 1}]
    (time
      (dotimes [_ 1000]
        (get m "/abba"))))

  (time
    (dotimes [_ 1000]
      (Util/matchURI "/user/1234/profile/compact/" 0 ["/user/" :id "/profile/" :type "/"] false)))

  ;; 124ns
  (cc/quick-bench
    (Util/matchURI "/user/1234/profile/compact/" 0 ["/user/" :id "/profile/" :type "/"] false))

  ;; 166ns
  (cc/quick-bench
    (impl/segments "/user/1234/profile/compact/"))

  ;; 597ns
  (let [s (s/create [["/user/:id/profile/:type/" 1]])]
    (cc/quick-bench
      (s/lookup s "/user/1234/profile/compact/")))

  (let [s (s/create [["/user/:id/profile/:type/" 1]])]
    (s/lookup s "/user/1234/profile/compact/")))
