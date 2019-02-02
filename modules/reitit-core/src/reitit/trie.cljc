(ns reitit.trie
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str])
  (:import [reitit Trie Trie$Match Trie$Matcher]))

(defrecord Wild [value])
(defrecord CatchAll [value])
(defrecord Match [data path-params])
(defrecord Node [children wilds catch-all data])

(defn wild? [x] (instance? Wild x))
(defn catch-all? [x] (instance? CatchAll x))

;; https://stackoverflow.com/questions/8033655/find-longest-common-prefix
(defn common-prefix [s1 s2]
  (let [max (min (count s1) (count s2))]
    (loop [i 0]
      (cond
        ;; full match
        (> i max)
        (subs s1 0 max)
        ;; partial match
        (not= (get s1 i) (get s2 i))
        (if-not (zero? i) (subs s1 0 i))
        ;; recur
        :else
        (recur (inc i))))))

(defn- -keyword [s]
  (if-let [i (str/index-of s "/")]
    (keyword (subs s 0 i) (subs s (inc i)))
    (keyword s)))

(defn split-path [s]
  (let [-static (fn [from to] (if-not (= from to) [(subs s from to)]))
        -wild (fn [from to] [(->Wild (-keyword (subs s (inc from) to)))])
        -catch-all (fn [from to] [(->CatchAll (keyword (subs s (inc from) to)))])]
    (loop [ss nil, from 0, to 0]
      (if (= to (count s))
        (concat ss (-static from to))
        (condp = (get s to)
          \{ (let [to' (or (str/index-of s "}" to) (throw (ex-info (str "Unbalanced brackets: " (pr-str s)) {})))]
               (if (= \* (get s (inc to)))
                 (recur (concat ss (-static from to) (-catch-all (inc to) to')) (inc to') (inc to'))
                 (recur (concat ss (-static from to) (-wild to to')) (inc to') (inc to'))))
          \: (let [to' (or (str/index-of s "/" to) (count s))]
               (recur (concat ss (-static from to) (-wild to to')) to' to'))
          \* (let [to' (count s)]
               (recur (concat ss (-static from to) (-catch-all to to')) to' to'))
          (recur ss from (inc to)))))))

(defn join-path [xs]
  (reduce
    (fn [s x]
      (str s (cond
               (string? x) x
               (instance? Wild x) (str "{" (-> x :value str (subs 1)) "}")
               (instance? CatchAll x) (str "{*" (-> x :value str (subs 1)) "}"))))
    "" xs))

(defn- -node [m]
  (map->Node (merge {:children {}, :wilds {}, :catch-all {}} m)))

(defn- -insert [node [path & ps] data]
  (let [node' (cond

                (nil? path)
                (assoc node :data data)

                (instance? Wild path)
                (update-in node [:wilds (:value path)] (fn [n] (-insert (or n (-node {})) ps data)))

                (instance? CatchAll path)
                (assoc-in node [:catch-all (:value path)] (-node {:data data}))

                (str/blank? path)
                (-insert node ps data)

                :else
                (or
                  (reduce
                    (fn [_ [p n]]
                      (if-let [cp (common-prefix p path)]
                        (if (= cp p)
                          ;; insert into child node
                          (let [n' (-insert n (conj ps (subs path (count p))) data)]
                            (reduced (assoc-in node [:children p] n')))
                          ;; split child node
                          (let [rp (subs p (count cp))
                                rp' (subs path (count cp))
                                n' (-insert (-node {}) ps data)
                                n'' (-insert (-node {:children {rp n, rp' n'}}) nil nil)]
                            (reduced (update node :children (fn [children]
                                                              (-> children
                                                                  (dissoc p)
                                                                  (assoc cp n'')))))))))
                    nil (:children node))
                  ;; new child node
                  (assoc-in node [:children path] (-insert (-node {}) ps data))))]
    (if-let [child (get-in node' [:children ""])]
      ;; optimize by removing empty paths
      (-> (merge-with merge (dissoc node' :data) child)
          (update :children dissoc ""))
      node')))

;;
;; public api
;;

(defn insert
  ([routes]
   (insert nil routes))
  ([node routes]
   (reduce
     (fn [acc [p d]]
       (insert acc p d))
     node routes))
  ([node path data]
   (-insert (or node (-node {})) (split-path path) data)))

(defn ^Trie$Matcher compile [{:keys [data children wilds catch-all]}]
  (let [matchers (cond-> []
                         data (conj (Trie/dataMatcher data))
                         children (into (for [[p c] children] (Trie/staticMatcher p (compile c))))
                         wilds (into (for [[p c] wilds] (Trie/wildMatcher p (compile c))))
                         catch-all (into (for [[p c] catch-all] (Trie/catchAllMatcher p (:data c)))))]
    (if (rest matchers)
      (Trie/linearMatcher matchers)
      (first matchers))))

(defn pretty [matcher]
  (-> matcher str read-string eval))

(defn lookup [^Trie$Matcher matcher path]
  (if-let [match ^Trie$Match (Trie/lookup matcher ^String path)]
    (->Match (.data match) (.parameters match))))

(defn scanner [compiled-tries]
  (Trie/scanner compiled-tries))

;;
;; spike
;;

(->
  [["/v2/whoami" 1]
   ["/v2/users/:user-id/datasets" 2]
   ["/v2/public/projects/:project-id/datasets" 3]
   ["/v1/public/topics/:topic" 4]
   ["/v1/users/:user-id/orgs/:org-id" 5]
   ["/v1/search/topics/:term" 6]
   ["/v1/users/:user-id/invitations" 7]
   ["/v1/users/:user-id/topics" 9]
   ["/v1/users/:user-id/bookmarks/followers" 10]
   ["/v2/datasets/:dataset-id" 11]
   ["/v1/orgs/:org-id/usage-stats" 12]
   ["/v1/orgs/:org-id/devices/:client-id" 13]
   ["/v1/messages/user/:user-id" 14]
   ["/v1/users/:user-id/devices" 15]
   ["/v1/public/users/:user-id" 16]
   ["/v1/orgs/:org-id/errors" 17]
   ["/v1/public/orgs/:org-id" 18]
   ["/v1/orgs/:org-id/invitations" 19]
   ["/v1/users/:user-id/device-errors" 22]
   ["/v2/login" 23]
   ["/v1/users/:user-id/usage-stats" 24]
   ["/v2/users/:user-id/devices" 25]
   ["/v1/users/:user-id/claim-device/:client-id" 26]
   ["/v2/public/projects/:project-id" 27]
   ["/v2/public/datasets/:dataset-id" 28]
   ["/v2/users/:user-id/topics/bulk" 29]
   ["/v1/messages/device/:client-id" 30]
   ["/v1/users/:user-id/owned-orgs" 31]
   ["/v1/topics/:topic" 32]
   ["/v1/users/:user-id/bookmark/:topic" 33]
   ["/v1/orgs/:org-id/members/:user-id" 34]
   ["/v1/users/:user-id/devices/:client-id" 35]
   ["/v1/users/:user-id" 36]
   ["/v1/orgs/:org-id/devices" 37]
   ["/v1/orgs/:org-id/members" 38]
   ["/v2/orgs/:org-id/topics" 40]
   ["/v1/whoami" 41]
   ["/v1/orgs/:org-id" 42]
   ["/v1/users/:user-id/api-key" 43]
   ["/v2/schemas" 44]
   ["/v2/users/:user-id/topics" 45]
   ["/v1/orgs/:org-id/confirm-membership/:token" 46]
   ["/v2/topics/:topic" 47]
   ["/v1/messages/topic/:topic" 48]
   ["/v1/users/:user-id/devices/:client-id/reset-password" 49]
   ["/v2/topics" 50]
   ["/v1/login" 51]
   ["/v1/users/:user-id/orgs" 52]
   ["/v2/public/messages/dataset/:dataset-id" 53]
   ["/v1/topics" 54]
   ["/v1/orgs" 55]
   ["/v1/users/:user-id/bookmarks" 56]
   ["/v1/orgs/:org-id/topics" 57]]
  (insert)
  (compile)
  (pretty))

(-> [["/kikka" 2]
     ["/kikka/kakka/kukka" 3]
     ["/kikka/:kakka/kurkku" 4]
     ["/kikka/kuri/{user/doc}/html" 5]]
    (insert)
    (compile)
    (pretty))
