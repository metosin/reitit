(ns reitit.opensensors-perf-test
  (:require [clojure.test :refer [deftest testing is]]
            [criterium.core :as cc]
            [reitit.perf-utils :refer :all]
            [cheshire.core :as json]
            [clojure.string :as str]
            [reitit.core :as reitit]
            [reitit.ring :as ring]

            [bidi.bidi :as bidi]

            [ataraxy.core :as ataraxy]

            [compojure.core :refer [routes context ANY]]

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

(comment
  (swagger->routes "https://api.opensensors.io/doc" false))

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

(comment
  (declare routes)
  (declare context)
  (declare ANY))

(def opensensors-compojure-routes
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
  (bench opensensors-routes (fn [path] {:request-method :get, :uri path, :path-info path}) false)
  (bench opensensors-routes (fn [path] {:request-method :get, :uri path, :path-info path}) true))

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
    (let [match (pedestal/find-route opensensors-pedestal-routes {:path-info route :request-method :get})]
      (if-not match
        (println route)))))

(defn bench-rest! []
  (let [routes opensensors-routes
        router (reitit/router routes)
        reitit-f #(reitit/match-by-path router (:uri %))
        reitit-ring-f (ring/ring-handler (ring/router opensensors-routes))
        bidi-f #(bidi/match-route opensensors-bidi-routes (:uri %))
        ataraxy-f (partial ataraxy/matches opensensors-ataraxy-routes)
        compojure-f opensensors-compojure-routes
        pedestal-f (partial pedestal/find-route opensensors-pedestal-routes)
        b! (partial bench!! routes (fn [path] {:request-method :get, :uri path, :path-info path}) true)]

    ;;  2538ns
    ;;  2065ns
    ;;   662ns (prefix-tree-router)
    ;;   567ns (segment-router)
    (b! "reitit" reitit-f)

    ;;  2845ns
    ;;  2316ns
    ;;   819ns (prefix-tree-router)
    ;;   723ns (segment-router)
    (b! "reitit-ring" reitit-ring-f)

    ;;  2821ns
    (b! "pedestal" pedestal-f)

    ;; 11615ns
    (b! "compojure" compojure-f)

    ;; 15034ns
    (b! "bidi" bidi-f)

    ;; 19688ns
    (b! "ataraxy" ataraxy-f)))

(comment
  (bench-rest!))
