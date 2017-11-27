(ns reitit.prefix-tree-perf-test
  (:require [clojure.test :refer :all]
            [io.pedestal.http.route.prefix-tree :as p]
            [reitit.segment :as segment]
            [criterium.core :as cc]))

;;
;; testing
;;

(def routes
  [["/v2/whoami" {:name :test/route1}]
   ["/v2/users/:user-id/datasets" {:name :test/route2}]
   ["/v2/public/projects/:project-id/datasets" {:name :test/route3}]
   ["/v1/public/topics/:topic" {:name :test/route4}]
   ["/v1/users/:user-id/orgs/:org-id" {:name :test/route5}]
   ["/v1/search/topics/:term" {:name :test/route6}]
   ["/v1/users/:user-id/invitations" {:name :test/route7}]
   ["/v1/users/:user-id/topics" {:name :test/route9}]
   ["/v1/users/:user-id/bookmarks/followers" {:name :test/route10}]
   ["/v2/datasets/:dataset-id" {:name :test/route11}]
   ["/v1/orgs/:org-id/usage-stats" {:name :test/route12}]
   ["/v1/orgs/:org-id/devices/:client-id" {:name :test/route13}]
   ["/v1/messages/user/:user-id" {:name :test/route14}]
   ["/v1/users/:user-id/devices" {:name :test/route15}]
   ["/v1/public/users/:user-id" {:name :test/route16}]
   ["/v1/orgs/:org-id/errors" {:name :test/route17}]
   ["/v1/public/orgs/:org-id" {:name :test/route18}]
   ["/v1/orgs/:org-id/invitations" {:name :test/route19}]
   ["/v1/users/:user-id/device-errors" {:name :test/route22}]
   ["/v2/login" {:name :test/route23}]
   ["/v1/users/:user-id/usage-stats" {:name :test/route24}]
   ["/v2/users/:user-id/devices" {:name :test/route25}]
   ["/v1/users/:user-id/claim-device/:client-id" {:name :test/route26}]
   ["/v2/public/projects/:project-id" {:name :test/route27}]
   ["/v2/public/datasets/:dataset-id" {:name :test/route28}]
   ["/v2/users/:user-id/topics/bulk" {:name :test/route29}]
   ["/v1/messages/device/:client-id" {:name :test/route30}]
   ["/v1/users/:user-id/owned-orgs" {:name :test/route31}]
   ["/v1/topics/:topic" {:name :test/route32}]
   ["/v1/users/:user-id/bookmark/:topic" {:name :test/route33}]
   ["/v1/orgs/:org-id/members/:user-id" {:name :test/route34}]
   ["/v1/users/:user-id/devices/:client-id" {:name :test/route35}]
   ["/v1/users/:user-id" {:name :test/route36}]
   ["/v1/orgs/:org-id/devices" {:name :test/route37}]
   ["/v1/orgs/:org-id/members" {:name :test/route38}]
   ["/v2/orgs/:org-id/topics" {:name :test/route40}]
   ["/v1/whoami" {:name :test/route41}]
   ["/v1/orgs/:org-id" {:name :test/route42}]
   ["/v1/users/:user-id/api-key" {:name :test/route43}]
   ["/v2/schemas" {:name :test/route44}]
   ["/v2/users/:user-id/topics" {:name :test/route45}]
   ["/v1/orgs/:org-id/confirm-membership/:token" {:name :test/route46}]
   ["/v2/topics/:topic" {:name :test/route47}]
   ["/v1/messages/topic/:topic" {:name :test/route48}]
   ["/v1/users/:user-id/devices/:client-id/reset-password" {:name :test/route49}]
   ["/v2/topics" {:name :test/route50}]
   ["/v1/login" {:name :test/route51}]
   ["/v1/users/:user-id/orgs" {:name :test/route52}]
   ["/v2/public/messages/dataset/:dataset-id" {:name :test/route53}]
   ["/v1/topics" {:name :test/route54}]
   ["/v1/orgs" {:name :test/route55}]
   ["/v1/users/:user-id/bookmarks" {:name :test/route56}]
   ["/v1/orgs/:org-id/topics" {:name :test/route57}]])

(def pedestal-tree
  (reduce
    (fn [acc [p d]]
      (p/insert acc p d))
    nil routes))

#_(def reitit-tree
  (reduce
    (fn [acc [p d]]
      (trie/insert acc p d))
    nil routes))

(def reitit-segment
  (segment/create routes))

(defn bench! []

  ;; 2.3ms
  (cc/quick-bench
    (dotimes [_ 1000]
      (p/lookup pedestal-tree "/v1/orgs/1/topics")))

  ;; 3.1ms
  ;; 2.5ms (string equals)
  ;; 2.5ms (protocol)
  ;; 2.3ms (nil childs)
  ;; 2.0ms (rando impros)
  ;; 1.9ms (wild & catch shortcuts)
  ;; 1.5ms (inline child fetching)
  ;; 1.5ms (WildNode also backtracks)
  ;; 1.4ms (precalculate segment-size)
  ;; 1.3ms (fast-map)
  ;; 1.3ms (dissoc wild & catch-all from children)
  ;; 1.3ms (reified protocols)
  ;; 0.8ms (flattened matching)
  ;; 0.8ms (return route-data)
  ;; 0.8ms (fix payloads)
  #_(cc/quick-bench
    (dotimes [_ 1000]
      (trie/lookup reitit-tree "/v1/orgs/1/topics" {})))

  ;;  0.9ms (initial)
  ;;  0.5ms (protocols)
  ;;  1.0ms (with path params)
  ;;  1.0ms (Match records)
  ;; 0.63ms (Single sweep path params)
  ;; 0.51ms (Cleanup)
  (cc/quick-bench
    (dotimes [_ 1000]
      (segment/lookup reitit-segment "/v1/orgs/1/topics"))))

(comment
  (bench!))

(comment
  (p/lookup pedestal-tree "/v1/orgs/1/topics")
  #_(trie/lookup reitit-tree "/v1/orgs/1/topics" {})
  (segment/lookup reitit-segment "/v1/orgs/1/topics"))
