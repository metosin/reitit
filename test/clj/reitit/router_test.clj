(ns reitit.router-test
  (:require [clojure.test :refer [deftest testing is]]
            [cheshire.core :as json]
            [clojure.string :as str]
            [reitit.core :as reitit]))

;;
;; extract sample routes
;;

(defn swagger->routes [url ring?]
  (let [route-number (atom 0)
        ->route-name #(keyword "test" (str "route" (swap! route-number inc)))
        ->endpoint (fn [m]
                     (if ring?
                       (reduce-kv
                         (fn [acc k v]
                           (assoc acc k {:handler #'identity, :name (->route-name)}))
                         {} (select-keys m #{:get :head :patch :delete :options :post :put}))
                       (->route-name)))]
    (-> (slurp url)
        (json/parse-string true)
        (->> :paths
             (mapv (fn [[p v]] [(-> p name (str/replace #"\{(.*?)\}" ":$1") (->> (str "/"))) (->endpoint v)]))))))

(defn valid-urls [router]
  (->>
    (for [name (reitit/route-names router)
          :let [match (reitit/match-by-name router name)
                params (if (reitit/partial-match? match)
                         (-> match :required (zipmap (range))))]]
      (:path (reitit/match-by-name router name params)))
    (into [])))

(comment
  (swagger->routes "https://api.opensensors.io/doc" false))

(defn bench-routes [routes f]
  (let [router (reitit/router routes)
        urls (valid-urls router)
        random-url #(rand-nth urls)
        log-time #(let [now (System/nanoTime)] (%) (- (System/nanoTime) now))
        total 10000
        dropped (int (* total 0.45))]
    (mapv
      #(let [times (->> (range total)
                        (mapv
                          (fn [_]
                            (let [now (System/nanoTime)]
                              (f %)
                              (- (System/nanoTime) now))))
                        (sort)
                        (drop dropped)
                        (drop-last dropped))
             avg (int (/ (reduce + times) (count times)))]
         [(-> % f :template) avg]) urls)))

(defn bench [routes no-paths?]
  (let [routes (mapv (fn [[path name]]
                       (if no-paths?
                         [(str/replace path #"\:" "") name]
                         [path name])) routes)
        router (reitit/router routes)]
    (doseq [[path time] (bench-routes routes #(reitit/match-by-path router %))]
      (println path "\t" time))))

;;
;; Perf tests
;;

(def opensensors-routes
  [["/v2/whoami" :test/route1]
   ["/v2/users/:user-id/datasets" :test/route2]
   ["/v2/public/projects/:project-id/datasets" :test/route3]
   ["/v1/public/topics/:topic" :test/route4]
   ["/v1/users/:user-id/orgs/:org-id" :test/route5]
   ["/v1/search/topics/:term" :test/route6]
   ["/v1/users/:user-id/invitations" :test/route7]
   ["/v1/orgs/:org-id/devices/:batch/:type" :test/route8]
   ["/v1/users/:user-id/topics" :test/route9]
   ["/v1/users/:user-id/bookmarks/followers" :test/route10]
   ["/v2/datasets/:dataset-id" :test/route11]
   ["/v1/orgs/:org-id/usage-stats" :test/route12]
   ["/v1/orgs/:org-id/devices/:client-id" :test/route13]
   ["/v1/messages/user/:user-id" :test/route14]
   ["/v1/users/:user-id/devices" :test/route15]
   ["/v1/public/users/:user-id" :test/route16]
   ["/v1/orgs/:org-id/errors" :test/route17]
   ["/v1/public/orgs/:org-id" :test/route18]
   ["/v1/orgs/:org-id/invitations" :test/route19]
   ["/v2/public/messages/dataset/bulk" :test/route20]
   ["/v1/users/:user-id/devices/bulk" :test/route21]
   ["/v1/users/:user-id/device-errors" :test/route22]
   ["/v2/login" :test/route23]
   ["/v1/users/:user-id/usage-stats" :test/route24]
   ["/v2/users/:user-id/devices" :test/route25]
   ["/v1/users/:user-id/claim-device/:client-id" :test/route26]
   ["/v2/public/projects/:project-id" :test/route27]
   ["/v2/public/datasets/:dataset-id" :test/route28]
   ["/v2/users/:user-id/topics/bulk" :test/route29]
   ["/v1/messages/device/:client-id" :test/route30]
   ["/v1/users/:user-id/owned-orgs" :test/route31]
   ["/v1/topics/:topic" :test/route32]
   ["/v1/users/:user-id/bookmark/:topic" :test/route33]
   ["/v1/orgs/:org-id/members/:user-id" :test/route34]
   ["/v1/users/:user-id/devices/:client-id" :test/route35]
   ["/v1/users/:user-id" :test/route36]
   ["/v1/orgs/:org-id/devices" :test/route37]
   ["/v1/orgs/:org-id/members" :test/route38]
   ["/v1/orgs/:org-id/members/invitation-data/:user-id" :test/route39]
   ["/v2/orgs/:org-id/topics" :test/route40]
   ["/v1/whoami" :test/route41]
   ["/v1/orgs/:org-id" :test/route42]
   ["/v1/users/:user-id/api-key" :test/route43]
   ["/v2/schemas" :test/route44]
   ["/v2/users/:user-id/topics" :test/route45]
   ["/v1/orgs/:org-id/confirm-membership/:token" :test/route46]
   ["/v2/topics/:topic" :test/route47]
   ["/v1/messages/topic/:topic" :test/route48]
   ["/v1/users/:user-id/devices/:client-id/reset-password" :test/route49]
   ["/v2/topics" :test/route50]
   ["/v1/login" :test/route51]
   ["/v1/users/:user-id/orgs" :test/route52]
   ["/v2/public/messages/dataset/:dataset-id" :test/route53]
   ["/v1/topics" :test/route54]
   ["/v1/orgs" :test/route55]
   ["/v1/users/:user-id/bookmarks" :test/route56]
   ["/v1/orgs/:org-id/topics" :test/route57]])

(comment
  (bench opensensors-routes false)
  (bench opensensors-routes true))
