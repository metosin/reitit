(ns reitit.opensensors-routing-test
  (:require [clojure.test :refer [deftest testing is]]
            [criterium.core :as cc]
            [reitit.perf-utils :refer :all]
            [cheshire.core :as json]
            [clojure.string :as str]
            [reitit.core :as reitit]
            [reitit.ring :as ring]

            [bidi.bidi :as bidi]

            [ataraxy.core :as ataraxy]

            [compojure.api.sweet :refer [api routes context ANY]]

            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.route.map-tree :as map-tree]
            [io.pedestal.http.route.router :as pedestal]
            [io.pedestal.http.route :as route]))

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
                            (let [now (System/nanoTime)
                                  result (f %)
                                  total (- (System/nanoTime) now)]
                              (assert result)
                              total)))
                        (sort)
                        (drop dropped)
                        (drop-last dropped))
             avg (int (/ (reduce + times) (count times)))]
         [% avg]) urls)))

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

(def handler (constantly {:status 200, :body "ok"}))

(def opensensors-routes
  [["/v2/whoami" {:handler handler, :name :test/route1}]
   ["/v2/users/:user-id/datasets" {:handler handler, :name :test/route2}]
   ["/v2/public/projects/:project-id/datasets" {:handler handler, :name :test/route3}]
   ["/v1/public/topics/:topic" {:handler handler, :name :test/route4}]
   ["/v1/users/:user-id/orgs/:org-id" {:handler handler, :name :test/route5}]
   ["/v1/search/topics/:term" {:handler handler, :name :test/route6}]
   ["/v1/users/:user-id/invitations" {:handler handler, :name :test/route7}]
   #_["/v1/orgs/:org-id/devices/:batch/:type" {:handler handler, :name :test/route8}]
   ["/v1/users/:user-id/topics" {:handler handler, :name :test/route9}]
   ["/v1/users/:user-id/bookmarks/followers" {:handler handler, :name :test/route10}]
   ["/v2/datasets/:dataset-id" {:handler handler, :name :test/route11}]
   ["/v1/orgs/:org-id/usage-stats" {:handler handler, :name :test/route12}]
   ["/v1/orgs/:org-id/devices/:client-id" {:handler handler, :name :test/route13}]
   ["/v1/messages/user/:user-id" {:handler handler, :name :test/route14}]
   ["/v1/users/:user-id/devices" {:handler handler, :name :test/route15}]
   ["/v1/public/users/:user-id" {:handler handler, :name :test/route16}]
   ["/v1/orgs/:org-id/errors" {:handler handler, :name :test/route17}]
   ["/v1/public/orgs/:org-id" {:handler handler, :name :test/route18}]
   ["/v1/orgs/:org-id/invitations" {:handler handler, :name :test/route19}]
   #_["/v2/public/messages/dataset/bulk" {:handler handler, :name :test/route20}]
   #_["/v1/users/:user-id/devices/bulk" {:handler handler, :name :test/route21}]
   ["/v1/users/:user-id/device-errors" {:handler handler, :name :test/route22}]
   ["/v2/login" {:handler handler, :name :test/route23}]
   ["/v1/users/:user-id/usage-stats" {:handler handler, :name :test/route24}]
   ["/v2/users/:user-id/devices" {:handler handler, :name :test/route25}]
   ["/v1/users/:user-id/claim-device/:client-id" {:handler handler, :name :test/route26}]
   ["/v2/public/projects/:project-id" {:handler handler, :name :test/route27}]
   ["/v2/public/datasets/:dataset-id" {:handler handler, :name :test/route28}]
   ["/v2/users/:user-id/topics/bulk" {:handler handler, :name :test/route29}]
   ["/v1/messages/device/:client-id" {:handler handler, :name :test/route30}]
   ["/v1/users/:user-id/owned-orgs" {:handler handler, :name :test/route31}]
   ["/v1/topics/:topic" {:handler handler, :name :test/route32}]
   ["/v1/users/:user-id/bookmark/:topic" {:handler handler, :name :test/route33}]
   ["/v1/orgs/:org-id/members/:user-id" {:handler handler, :name :test/route34}]
   ["/v1/users/:user-id/devices/:client-id" {:handler handler, :name :test/route35}]
   ["/v1/users/:user-id" {:handler handler, :name :test/route36}]
   ["/v1/orgs/:org-id/devices" {:handler handler, :name :test/route37}]
   ["/v1/orgs/:org-id/members" {:handler handler, :name :test/route38}]
   #_["/v1/orgs/:org-id/members/invitation-data/:user-id" {:handler handler, :name :test/route39}]
   ["/v2/orgs/:org-id/topics" {:handler handler, :name :test/route40}]
   ["/v1/whoami" {:handler handler, :name :test/route41}]
   ["/v1/orgs/:org-id" {:handler handler, :name :test/route42}]
   ["/v1/users/:user-id/api-key" {:handler handler, :name :test/route43}]
   ["/v2/schemas" {:handler handler, :name :test/route44}]
   ["/v2/users/:user-id/topics" {:handler handler, :name :test/route45}]
   ["/v1/orgs/:org-id/confirm-membership/:token" {:handler handler, :name :test/route46}]
   ["/v2/topics/:topic" {:handler handler, :name :test/route47}]
   ["/v1/messages/topic/:topic" {:handler handler, :name :test/route48}]
   ["/v1/users/:user-id/devices/:client-id/reset-password" {:handler handler, :name :test/route49}]
   ["/v2/topics" {:handler handler, :name :test/route50}]
   ["/v1/login" {:handler handler, :name :test/route51}]
   ["/v1/users/:user-id/orgs" {:handler handler, :name :test/route52}]
   ["/v2/public/messages/dataset/:dataset-id" {:handler handler, :name :test/route53}]
   ["/v1/topics" {:handler handler, :name :test/route54}]
   ["/v1/orgs" {:handler handler, :name :test/route55}]
   ["/v1/users/:user-id/bookmarks" {:handler handler, :name :test/route56}]
   ["/v1/orgs/:org-id/topics" {:handler handler, :name :test/route57}]])

(def opensensors-bidi-routes
  ["/" {"v1/" {"public/" {["topics/" :topic] :test/route4
                          ["users/" :user-id] :test/route16
                          ["orgs/" :org-id] :test/route18}
               ["users/" :user-id] {["/orgs/" :org-id] :test/route5
                                    "/invitations" :test/route7
                                    "/topics" :route9
                                    "/bookmarks/followers" :test/route10
                                    "/devices" {"" :route15
                                                #_#_"/bulk" :test/route21
                                                ["/" :client-id] :test/route35
                                                ["/" :client-id "/reset-password"] :test/route49}
                                    "/device-errors" :test/route22
                                    "/usage-stats" :test/route24
                                    ["/claim-device/" :client-id] :test/route26
                                    "/owned-orgs" :test/route31
                                    ["/bookmark/" :topic] :test/route33
                                    "" :test/route36
                                    "/orgs" :test/route52
                                    "/api-key" :test/route43
                                    "/bookmarks" :test/route56}
               ["search/topics/" :term] :test/route6
               "orgs" {"" :test/route55
                       ["/" :org-id] {"/devices" {"" :test/route37
                                                  ["/" :device-id] :test/route13
                                                  #_#_["/" :batch "/" :type] :test/route8}
                                      "/usage-stats" :test/route12
                                      "/invitations" :test/route19
                                      "/members" {["/" :user-id] :test/route34
                                                  "" :test/route38
                                                  #_#_["/invitation-data/" :user-id] :test/route39}
                                      "/errors" :test/route17
                                      "" :test/route42
                                      ["/confirm-membership/" :token] :test/route46
                                      "/topics" :test/route57}}
               "messages/" {["user/" :user-id] :test/route14
                            ["device/" :client-id] :test/route30
                            ["topic/" :topic] :test/route48}
               "topics" {["/" :topic] :test/route32
                         "" :test/route54}
               "whoami" :test/route41
               "login" :test/route51}
        "v2/" {"whoami" :test/route1
               ["users/" :user-id] {"/datasets" :test/route2
                                    "/devices" :test/route25
                                    "/topics" {"/bulk" :test/route29
                                               "" :test/route45}}
               "public/" {["projects/" :project-id] {"/datasets" :test/route3
                                                     "" :test/route27}
                          #_#_"messages/dataset/bulk" :test/route20
                          ["datasets/" :dataset-id] :test/route28
                          ["messages/dataset/" :dataset-id] :test/route53}
               ["datasets/" :dataset-id] :test/route11
               "login" :test/route23
               ["orgs/" :org-id "/topics"] :test/route40
               "schemas" :test/route44
               ["topics/" :topic] :test/route47
               "topics" :test/route50}}])

(def opensensors-ataraxy-routes
  (ataraxy/compile
    '{"/v1/" {"public/" {["topics/" topic] [:test/route4 topic]
                         ["users/" user-id] [:test/route16 user-id]
                         ["orgs/" org-id] [:test/route18 org-id]}
              ["users/" user-id] {["/orgs/" org-id] [:test/route5 user-id org-id]
                                  "/invitations" [:test/route7 user-id]
                                  "/topics" [:route9 user-id]
                                  "/bookmarks/followers" [:test/route10 user-id]
                                  "/devices" {"" [:route15 user-id]
                                              #_#_"/bulk" [:test/route21 user-id]
                                              ["/" client-id] [:test/route35 user-id client-id]
                                              ["/" client-id "/reset-password"] [:test/route49 user-id client-id]}
                                  "/device-errors" [:test/route22 user-id]
                                  "/usage-stats" [:test/route24 user-id]
                                  ["/claim-device/" client-id] [:test/route26 user-id client-id]
                                  "/owned-orgs" [:test/route31 user-id]
                                  ["/bookmark/" topic] [:test/route33 user-id topic]
                                  "" [:test/route36 user-id]
                                  "/orgs" [:test/route52 user-id]
                                  "/api-key" [:test/route43 user-id]
                                  "/bookmarks" [:test/route56 user-id]}
              ["search/topics/" term] [:test/route6 term]
              "orgs" {"" [:test/route55]
                      ["/" org-id] {"/devices" {"" [:test/route37 org-id]
                                                ["/" device-id] [:test/route13 org-id device-id]
                                                #_#_["/" batch "/" type] [:test/route8 org-id batch type]}
                                    "/usage-stats" [:test/route12 org-id]
                                    "/invitations" [:test/route19 org-id]
                                    "/members" {["/" user-id] [:test/route34 org-id user-id]
                                                "" [:test/route38 org-id]
                                                #_#_["/invitation-data/" user-id] [:test/route39 org-id user-id]}
                                    "/errors" [:test/route17 org-id]
                                    "" [:test/route42 org-id]
                                    ["/confirm-membership/" token] [:test/route46 org-id token]
                                    "/topics" [:test/route57 org-id]}}
              "messages/" {["user/" user-id] [:test/route14 user-id]
                           ["device/" client-id] [:test/route30 client-id]
                           ["topic/" topic] [:test/route48 topic]}
              "topics" {["/" topic] [:test/route32 topic]
                        "" [:test/route54]}
              "whoami" [:test/route41]
              "login" [:test/route51]}
      "/v2/" {"whoami" [:test/route1]
              ["users/" user-id] {"/datasets" [:test/route2 user-id]
                                  "/devices" [:test/route25 user-id]
                                  "/topics" {"/bulk" [:test/route29 user-id]
                                             "" [:test/route45 user-id]}}
              "public/" {["projects/" project-id] {"/datasets" [:test/route3 project-id]
                                                   "" [:test/route27 project-id]}
                         #_#_"messages/dataset/bulk" [:test/route20]
                         ["datasets/" dataset-id] [:test/route28 dataset-id]
                         ["messages/dataset/" dataset-id] [:test/route53 dataset-id]}
              ["datasets/" dataset-id] [:test/route11 dataset-id]
              "login" [:test/route23]
              ["orgs/" org-id "/topics"] [:test/route40 org-id]
              "schemas" [:test/route44]
              ["topics/" topic] [:test/route47 topic]
              "topics" [:test/route50]}}))

(def opensensors-compojure-api-routes
  (routes
    (context "/v1" []
      (context "/public" []
        (ANY "/topics/:topic" [] {:name :test/route4} handler)
        (ANY "/users/:user-id" [] {:name :test/route16} handler)
        (ANY "/orgs/:org-id" [] {:name :test/route18} handler))
      (context "/users/:user-id" []
        (ANY "/orgs/:org-id" [] {:name :test/route5} handler)
        (ANY "/invitations" [] {:name :test/route7} handler)
        (ANY "/topics" [] {:name :test/route9} handler)
        (ANY "/bookmarks/followers" [] {:name :test/route10} handler)
        (context "/devices" []
          (ANY "/" [] {:name :test/route15} handler)
          #_(ANY "/bulk" [] {:name :test/route21} handler)
          (ANY "/:client-id" [] {:name :test/route35} handler)
          (ANY "/:client-id/reset-password" [] {:name :test/route49} handler))
        (ANY "/device-errors" [] {:name :test/route22} handler)
        (ANY "/usage-stats" [] {:name :test/route24} handler)
        (ANY "/claim-device/:client-id" [] {:name :test/route26} handler)
        (ANY "/owned-orgs" [] {:name :test/route31} handler)
        (ANY "/bookmark/:topic" [] {:name :test/route33} handler)
        (ANY "/" [] {:name :test/route36} handler)
        (ANY "/orgs" [] {:name :test/route52} handler)
        (ANY "/api-key" [] {:name :test/route43} handler)
        (ANY "/bookmarks" [] {:name :test/route56} handler))
      (ANY "/search/topics/:term" [] {:name :test/route6} handler)
      (context "/orgs" []
        (ANY "/" [] {:name :test/route55} handler)
        (context "/:org-id" []
          (context "/devices" []
            (ANY "/" [] {:name :test/route37} handler)
            (ANY "/:device-id" [] {:name :test/route13} handler)
            #_(ANY "/:batch/:type" [] {:name :test/route8} handler))
          (ANY "/usage-stats" [] {:name :test/route12} handler)
          (ANY "/invitations" [] {:name :test/route19} handler)
          (context "/members" []
            (ANY "/:user-id" [] {:name :test/route34} handler)
            (ANY "/" [] {:name :test/route38} handler)
            #_(ANY "/invitation-data/:user-id" [] {:name :test/route39} handler))
          (ANY "/errors" [] {:name :test/route17} handler)
          (ANY "/" [] {:name :test/route42} handler)
          (ANY "/confirm-membership/:token" [] {:name :test/route46} handler)
          (ANY "/topics" [] {:name :test/route57} handler)))
      (context "/messages" []
        (ANY "/user/:user-id" [] {:name :test/route14} handler)
        (ANY "/device/:client-id" [] {:name :test/route30} handler)
        (ANY "/topic/:topic" [] {:name :test/route48} handler))
      (context "/topics" []
        (ANY "/:topic" [] {:name :test/route32} handler)
        (ANY "/" [] {:name :test/route54} handler))
      (ANY "/whoami" [] {:name :test/route41} handler)
      (ANY "/login" [] {:name :test/route51} handler))
    (context "/v2" []
      (ANY "/whoami" [] {:name :test/route1} handler)
      (context "/users/:user-id" []
        (ANY "/datasets" [] {:name :test/route2} handler)
        (ANY "/devices" [] {:name :test/route25} handler)
        (context "/topics" []
          (ANY "/bulk" [] {:name :test/route29} handler)
          (ANY "/" [] {:name :test/route54} handler))
        (ANY "/" [] {:name :test/route45} handler))
      (context "/public" []
        (context "/projects/:project-id" []
          (ANY "/datasets" [] {:name :test/route3} handler)
          (ANY "/" [] {:name :test/route27} handler))
        #_(ANY "/messages/dataset/bulk" [] {:name :test/route20} handler)
        (ANY "/datasets/:dataset-id" [] {:name :test/route28} handler)
        (ANY "/messages/dataset/:dataset-id" [] {:name :test/route53} handler))
      (ANY "/datasets/:dataset-id" [] {:name :test/route11} handler)
      (ANY "/login" [] {:name :test/route23} handler)
      (ANY "/orgs/:org-id/topics" [] {:name :test/route40} handler)
      (ANY "/schemas" [] {:name :test/route44} handler)
      (ANY "/topics/:topic" [] {:name :test/route47} handler)
      (ANY "/topics" [] {:name :test/route50} handler))))

(def opensensors-pedestal-routes
  (map-tree/router
    (table/table-routes
      [["/v2/whoami" :get handler :route-name :test/route1]
       ["/v2/users/:user-id/datasets" :get handler :route-name :test/route2]
       ["/v2/public/projects/:project-id/datasets" :get handler :route-name :test/route3]
       ["/v1/public/topics/:topic" :get handler :route-name :test/route4]
       ["/v1/users/:user-id/orgs/:org-id" :get handler :route-name :test/route5]
       ["/v1/search/topics/:term" :get handler :route-name :test/route6]
       ["/v1/users/:user-id/invitations" :get handler :route-name :test/route7]
       #_["/v1/orgs/:org-id/devices/:batch/:type" :get handler :route-name :test/route8]
       ["/v1/users/:user-id/topics" :get handler :route-name :test/route9]
       ["/v1/users/:user-id/bookmarks/followers" :get handler :route-name :test/route10]
       ["/v2/datasets/:dataset-id" :get handler :route-name :test/route11]
       ["/v1/orgs/:org-id/usage-stats" :get handler :route-name :test/route12]
       ["/v1/orgs/:org-id/devices/:client-id" :get handler :route-name :test/route13]
       ["/v1/messages/user/:user-id" :get handler :route-name :test/route14]
       ["/v1/users/:user-id/devices" :get handler :route-name :test/route15]
       ["/v1/public/users/:user-id" :get handler :route-name :test/route16]
       ["/v1/orgs/:org-id/errors" :get handler :route-name :test/route17]
       ["/v1/public/orgs/:org-id" :get handler :route-name :test/route18]
       ["/v1/orgs/:org-id/invitations" :get handler :route-name :test/route19]
       #_["/v2/public/messages/dataset/bulk" :get handler :route-name :test/route20]
       #_["/v1/users/:user-id/devices/bulk" :get handler :route-name :test/route21]
       ["/v1/users/:user-id/device-errors" :get handler :route-name :test/route22]
       ["/v2/login" :get handler :route-name :test/route23]
       ["/v1/users/:user-id/usage-stats" :get handler :route-name :test/route24]
       ["/v2/users/:user-id/devices" :get handler :route-name :test/route25]
       ["/v1/users/:user-id/claim-device/:client-id" :get handler :route-name :test/route26]
       ["/v2/public/projects/:project-id" :get handler :route-name :test/route27]
       ["/v2/public/datasets/:dataset-id" :get handler :route-name :test/route28]
       ["/v2/users/:user-id/topics/bulk" :get handler :route-name :test/route29]
       ["/v1/messages/device/:client-id" :get handler :route-name :test/route30]
       ["/v1/users/:user-id/owned-orgs" :get handler :route-name :test/route31]
       ["/v1/topics/:topic" :get handler :route-name :test/route32]
       ["/v1/users/:user-id/bookmark/:topic" :get handler :route-name :test/route33]
       ["/v1/orgs/:org-id/members/:user-id" :get handler :route-name :test/route34]
       ["/v1/users/:user-id/devices/:client-id" :get handler :route-name :test/route35]
       ["/v1/users/:user-id" :get handler :route-name :test/route36]
       ["/v1/orgs/:org-id/devices" :get handler :route-name :test/route37]
       ["/v1/orgs/:org-id/members" :get handler :route-name :test/route38]
       #_["/v1/orgs/:org-id/members/invitation-data/:user-id" :get handler :route-name :test/route39]
       ["/v2/orgs/:org-id/topics" :get handler :route-name :test/route40]
       ["/v1/whoami" :get handler :route-name :test/route41]
       ["/v1/orgs/:org-id" :get handler :route-name :test/route42]
       ["/v1/users/:user-id/api-key" :get handler :route-name :test/route43]
       ["/v2/schemas" :get handler :route-name :test/route44]
       ["/v2/users/:user-id/topics" :get handler :route-name :test/route45]
       ["/v1/orgs/:org-id/confirm-membership/:token" :get handler :route-name :test/route46]
       ["/v2/topics/:topic" :get handler :route-name :test/route47]
       ["/v1/messages/topic/:topic" :get handler :route-name :test/route48]
       ["/v1/users/:user-id/devices/:client-id/reset-password" :get handler :route-name :test/route49]
       ["/v2/topics" :get handler :route-name :test/route50]
       ["/v1/login" :get handler :route-name :test/route51]
       ["/v1/users/:user-id/orgs" :get handler :route-name :test/route52]
       ["/v2/public/messages/dataset/:dataset-id" :get handler :route-name :test/route53]
       ["/v1/topics" :get handler :route-name :test/route54]
       ["/v1/orgs" :get handler :route-name :test/route55]
       ["/v1/users/:user-id/bookmarks" :get handler :route-name :test/route56]
       ["/v1/orgs/:org-id/topics" :get handler :route-name :test/route57]])))

(comment
  (pedestal/find-route
    (map-tree/router
      (table/table-routes
        [["/v1/orgs/:org-id/members/:user-id" :get (constantly "") :route-name :test/route34]
         ["/v1/orgs/:org-id/members/invitation-data/:user-id" :get (constantly "") :route-name :test/route39]]))
    {:path-info "/v1/orgs/0/members/invitation-data/1" :request-method :get})

  (require '[io.pedestal.http.route.definition.table :as table])
  (require '[io.pedestal.http.route.map-tree :as map-tree])
  (require '[io.pedestal.http.route.router :as pedestal])

  (pedestal/find-route
    (map-tree/router
      (table/table-routes
        [["/:a" :get (constantly "") :route-name ::ping]
         ["/evil/ping" :get (constantly "") :route-name ::evil-ping]]))
    {:path-info "/evil/ping" :request-method :get}))

(doseq [route (valid-urls (reitit/router opensensors-routes))]
  (let [match (pedestal/find-route opensensors-pedestal-routes {:path-info route :request-method :get})]
    (if-not match
      (println route))))

(comment
  (bench opensensors-routes false)
  (bench opensensors-routes true))

(comment

  (doseq [route (valid-urls (reitit/router opensensors-routes))]
    (let [app (ring/ring-handler (ring/router opensensors-routes))
          match (app {:uri route :request-method :get})]
      (if-not match
        (println route))))

  (doseq [route (valid-urls (reitit/router opensensors-routes))]
    (let [match (bidi/match-route opensensors-bidi-routes route)]
      (if-not match
        (println route))))

  (doseq [route (valid-urls (reitit/router opensensors-routes))]
    (let [match (ataraxy/matches opensensors-ataraxy-routes {:uri route})]
      (if-not match
        (println route))))

  (doseq [route (valid-urls (reitit/router opensensors-routes))]
    (let [match (opensensors-compojure-api-routes {:uri route :request-method :get})]
      (if-not match
        (println route))))

  (doseq [route (valid-urls (reitit/router opensensors-routes))]
    (let [match (pedestal/find-route opensensors-pedestal-routes {:path-info route :request-method :get})]
      (if-not match
        (println route)))))

(defn bench!! [routes verbose? name f]
  (System/gc)
  (println)
  (suite name)
  (println)
  (let [times (for [[path time] (bench-routes routes f)]
                (do
                  (when verbose? (println (format "%7s" time) "\t" path))
                  time))]
    (title (str "average: " (int (/ (reduce + times) (count times)))))))

(defn bench-rest! []
  (let [routes opensensors-routes
        router (reitit/router routes)
        reitit-f #(reitit/match-by-path router %)
        reitit-ring-f (let [app (ring/ring-handler (ring/router opensensors-routes))]
                        #(app {:uri % :request-method :get}))
        bidi-f #(bidi/match-route opensensors-bidi-routes %)
        ataraxy-f #(ataraxy/matches opensensors-ataraxy-routes {:uri %})
        compojure-api-f #(opensensors-compojure-api-routes {:uri % :request-method :get})
        pedestal-f #(pedestal/find-route opensensors-pedestal-routes {:path-info % :request-method :get})]

    ;;  2538ns -> 2028ns
    (bench!! routes true "reitit" reitit-f)

    ;;  2845ns -> 2299ns
    (bench!! routes true "reitit-ring" reitit-ring-f)

    ;;  2737ns
    (bench!! routes true "pedestal" pedestal-f)

    ;;  9823ns
    (bench!! routes true "compojure-api" compojure-api-f)

    ;; 16716ns
    (bench!! routes true "bidi" bidi-f)

    ;; 24467ns
    (bench!! routes true "ataraxy" ataraxy-f)))

(comment
  (bench-rest!))

;;
;; CQRSish
;;

(def commands #{:upsert-appeal :upsert-appeal-verdict :delete-appeal :delete-appeal-verdict :mark-seen :mark-everything-seen :upsert-application-handler :remove-application-handler :cancel-inforequest :cancel-application :cancel-application-authority :undo-cancellation :request-for-complement :cleanup-krysp :submit-application :refresh-ktj :save-application-drawings :create-application :add-operation :update-op-description :change-primary-operation :change-permit-sub-type :change-location :change-application-state :return-to-draft :change-warranty-start-date :change-warranty-end-date :add-link-permit :remove-link-permit-by-app-id :create-change-permit :create-continuation-period-permit :convert-to-application :add-bulletin-comment :move-to-proclaimed :move-to-verdict-given :move-to-final :save-proclaimed-bulletin :save-verdict-given-bulletin :set-municipality-hears-neighbors :archive-documents :mark-pre-verdict-phase-archived :save-asianhallinta-config :create-assignment :update-assignment :complete-assignment :bind-attachment :bind-attachments :set-attachment-type :approve-attachment :reject-attachment :reject-attachment-note :create-attachments :create-ram-attachment :delete-attachment :delete-attachment-version :upload-attachment :rotate-pdf :upsert-stamp-template :delete-stamp-template :stamp-attachments :sign-attachments :set-attachment-meta :set-attachment-not-needed :set-attachments-as-verdict-attachment :set-attachment-as-construction-time :set-attachment-visibility :convert-to-pdfa :invite-with-role :approve-invite :decline-invitation :remove-auth :change-auth :unsubscribe-notifications :subscribe-notifications :set-calendar-enabled-for-authority :create-calendar-slots :update-calendar-slot :delete-calendar-slot :add-reservation-type-for-organization :update-reservation-type :delete-reservation-type :reserve-calendar-slot :accept-reservation :decline-reservation :cancel-reservation :mark-reservation-update-seen :add-campaign :delete-campaign :change-email-init :change-email :can-target-comment-to-authority :can-mark-answered :add-comment :company-update :company-lock :company-user-update :company-user-delete :company-user-delete-all :company-invite-user :company-add-user :company-invite :company-cancel-invite :save-company-tags :update-application-company-notes :inform-construction-started :inform-construction-ready :copy-application :update-3d-map-server-details :set-3d-map-enabled :redirect-to-3d-map :create-archiving-project :submit-archiving-project :create-doc :remove-doc :set-doc-status :update-doc :update-task :remove-document-data :approve-doc :reject-doc :reject-doc-note :set-user-to-document :set-current-user-to-document :set-company-to-document :set-feature :remove-uploaded-file :create-foreman-application :update-foreman-other-applications :link-foreman-task :update-guest-authority-organization :remove-guest-authority-organization :invite-guest :toggle-guest-subscription :delete-guest-application :info-link-delete :info-link-reorder :info-link-upsert :mark-seen-organization-links :create-inspection-summary-template :delete-inspection-summary-template :modify-inspection-summary-template :set-inspection-summary-template-for-operation :create-inspection-summary :delete-inspection-summary :toggle-inspection-summary-locking :add-target-to-inspection-summary :edit-inspection-summary-target :remove-target-from-inspection-summary :set-target-status :set-inspection-date :approve-application :move-attachments-to-backing-system :parties-as-krysp :merge-details-from-krysp :application-to-asianhallinta :attachments-to-asianhallinta :order-verdict-attachment-prints :frontend-log :reset-frontend-log :new-verdict-template :set-verdict-template-name :save-verdict-template-draft-value :publish-verdict-template :toggle-delete-verdict-template :copy-verdict-template :save-verdict-template-settings-value :add-verdict-template-review :update-verdict-template-review :add-verdict-template-plan :update-verdict-template-plan :set-default-operation-verdict-template :upsert-phrase :delete-phrase :neighbor-add :neighbor-add-owners :neighbor-update :neighbor-remove :neighbor-send-invite :neighbor-mark-done :neighbor-response :change-urgency :add-authority-notice :add-application-tags :init-sign :cancel-sign :convert-to-normal-inforequests :update-organization :add-scope :create-organization :add-organization-link :update-organization-link :remove-organization-link :update-allowed-autologin-ips :set-organization-selected-operations :organization-operations-attachments :set-organization-app-required-fields-filling-obligatory :set-automatic-ok-for-attachments :set-organization-assignments :set-organization-inspection-summaries :set-organization-extended-construction-waste-report :set-organization-validate-verdict-given-date :set-organization-use-attachment-links-integration :set-organization-calendars-enabled :set-organization-boolean-attribute :set-organization-permanent-archive-start-date :set-organization-neighbor-order-email :set-organization-submit-notification-email :set-organization-inforequest-notification-email :set-organization-default-reservation-location :set-krysp-endpoint :set-kopiolaitos-info :save-vendor-backend-redirect-config :update-organization-name :save-organization-tags :update-map-server-details :update-user-layers :update-suti-server-details :section-toggle-enabled :section-toggle-operation :upsert-handler-role :toggle-handler-role :upsert-assignment-trigger :remove-assignment-trigger :update-docstore-info :browser-timing :create-application-from-previous-permit :screenmessages-add :screenmessages-reset :add-single-sign-on-key :update-single-sign-on-key :remove-single-sign-on-key :create-statement-giver :delete-statement-giver :request-for-statement :ely-statement-request :delete-statement :save-statement-as-draft :give-statement :request-for-statement-reply :save-statement-reply-as-draft :reply-statement :suti-toggle-enabled :suti-toggle-operation :suti-www :suti-update-id :suti-update-added :create-task :delete-task :approve-task :reject-task :review-done :mark-review-faulty :resend-review-to-backing-system :set-tos-function-for-operation :remove-tos-function-from-operation :set-tos-function-for-application :force-fix-tos-function-for-application :store-tos-metadata-for-attachment :store-tos-metadata-for-application :store-tos-metadata-for-process :set-myyntipalvelu-for-attachment :create-user :create-rest-api-user :update-user :applicant-to-authority :update-default-application-filter :save-application-filter :remove-application-filter :update-user-organization :remove-user-organization :update-user-roles :check-password :change-passwd :reset-password :admin-reset-password :set-user-enabled :login :impersonate-authority :register-user :confirm-account-link :retry-rakentajafi :remove-user-attachment :copy-user-attachments-to-application :remove-user-notification :notifications-update :check-for-verdict :new-verdict-draft :save-verdict-draft :publish-verdict :delete-verdict :sign-verdict :create-digging-permit})

(def queries #{:comments :actions :allowed-actions :allowed-actions-for-category :admin-attachment-report :appeals :application :application-authorities :application-commenters :enable-accordions :party-document-names :application-submittable :inforequest-markers :change-application-state-targets :link-permit-required :app-matches-for-link-permits :all-operations-in :application-handlers :application-organization-handler-roles :application-organization-archive-enabled :application-bulletins :application-bulletin-municipalities :application-bulletin-states :bulletin :bulletin-versions :bulletin-comments :publish-bulletin-enabled :municipality-hears-neighbors-visible :applications-search :applications-search-default :applications-for-new-appointment-page :get-application-operations :applications :latest-applications :event-search :tasks-tab-visible :application-info-tab-visible :application-summary-tab-visible :application-verdict-tab-visible :document-states :archiving-operations-enabled :permanent-archive-enabled :application-in-final-archiving-state :asianhallinta-config :assignments-for-application :assignment-targets :assignments-search :assignment-count :assignments :assignment :bind-attachments-job :attachments :attachment :attachment-groups :attachments-filters :attachments-tag-groups :attachment-types :ram-linked-attachments :attachment-operations :stamp-templates :custom-stamps :stamp-attachments-job :signing-possible :set-attachment-group-enabled :invites :my-calendars :calendar :calendars-for-authority-admin :calendar-slots :reservation-types-for-organization :available-calendar-slots :application-calendar-config :calendar-actions-required :applications-with-appointments :my-reserved-slots :campaigns :campaign :company :company-users-for-person-selector :company-tags :companies :user-company-locked :company-search-user :remove-company-tag-ok :company-notes :enable-company-search :info-construction-status :copy-application-invite-candidates :application-copyable-to-location :application-copyable :source-application :user-is-pure-digitizer :digitizing-enabled :document :validate-doc :fetch-validation-errors :schemas :features :apply-fixture :foreman-history :foreman-applications :resolve-guest-authority-candidate :guest-authorities-organization :application-guests :guest-authorities-application-organization :get-link-account-token :info-links :organization-links :organization-inspection-summary-settings :inspection-summaries-for-application :get-building-info-from-wfs :external-api-enabled :integration-messages :ely-statement-types :frontend-log-entries :newest-version :verdict-templates :verdict-template-categories :verdict-template :verdict-template-settings :verdict-template-reviews :verdict-template-plans :default-operation-verdict-templates :organization-phrases :application-phrases :owners :application-property-owners :municipality-borders :active-municipalities :municipality-active :neighbor-application :authority-notice :find-sign-process :organization-by-user :all-attachment-types-by-user :organization-name-by-user :user-organizations-for-permit-type :user-organizations-for-archiving-project :organizations :allowed-autologin-ips-for-organization :organization-by-id :permit-types :municipalities-with-organization :municipalities :all-operations-for-organization :selected-operations-for-municipality :addable-operations :organization-details :krysp-config :kopiolaitos-config :get-organization-names :vendor-backend-redirect-config :remove-tag-ok :get-organization-tags :get-organization-areas :get-map-layers-data :municipality-for-property :property-borders :screenmessages :get-single-sign-on-keys :get-organizations-statement-givers :get-possible-statement-statuses :get-statement-givers :statement-replies-enabled :statement-is-replyable :authorized-for-requesting-statement-reply :statement-attachment-allowed :statements-after-approve-allowed :neighbors-statement-enabled :suti-admin-details :suti-operations :suti-application-data :suti-application-products :suti-pre-sent-state :task-types-for-application :review-can-be-marked-done :is-end-review :available-tos-functions :tos-metadata-schema :case-file-data :tos-operations-enabled :common-area-application :user :users :users-in-same-organizations :user-by-email :users-for-datatables :saved-application-filters :redirect-after-login :user-attachments :add-user-attachment-allowed :email-in-use :enable-foreman-search :calendars-enabled :verdict-attachment-type :selected-digging-operations-for-organization :ya-extensions :approve-ya-extension})

(def cqrs-routes
  (mapv (fn [command] [(str "/command/" (name command)) {:post handler :name command}]) commands))

(def cqrs-routes-pedestal
  (map-tree/router
    (table/table-routes
      (mapv (fn [command] [(str "/command/" (name command)) :post handler :route-name command]) commands))))

(class (:tree-map cqrs-routes-pedestal))

(class (:data (ring/router cqrs-routes)))

(comment

  (doseq [route (valid-urls (reitit/router cqrs-routes))]
    (let [app (ring/ring-handler (ring/router cqrs-routes))
          match (app {:uri route :request-method :post})]
      (if-not match
        (println route))))

  (doseq [route (valid-urls (reitit/router cqrs-routes))]
    (let [match (pedestal/find-route cqrs-routes-pedestal {:path-info route :request-method :post})]
      (if-not match
        (println route)))))

(defn bench-cqrs! []
  (let [routes cqrs-routes
        router (reitit/router cqrs-routes)
        reitit-f #(reitit/match-by-path router %)
        reitit-ring-f (let [app (ring/ring-handler (ring/router routes))]
                        #(app {:uri % :request-method :post}))
        pedestal-f #(pedestal/find-route cqrs-routes-pedestal {:path-info % :request-method :post})]

    ;;  125ns
    ;;   62ns (fast-map)
    (bench!! routes false "reitit" reitit-f)

    ;;  272ns
    ;;  219ns (fast-assoc)
    ;;  171ns (fast-map)
    (bench!! routes false "reitit-ring" reitit-ring-f)

    ;;  172ns
    (bench!! routes false "pedestal" pedestal-f)))

(comment
  (bench-cqrs!))
