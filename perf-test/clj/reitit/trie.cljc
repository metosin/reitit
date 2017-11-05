(ns reitit.trie
  (:require [clojure.walk :as walk]
            [clojure.string :as str]
            [criterium.core :as cc]
            [reitit.impl :as impl]))

(set! *warn-on-reflection* true)

;;
;; Prefix-tree-router
;;

(defn- char-key
  "Return the single character child key for the string started at
  index i."
  [s i]
  (if (< i (count s))
    (subs s i (inc i))))

(defprotocol Lookup
  (lookup [this path params]))

(extend-protocol Lookup
  nil
  (lookup [_ _ _]))

(defrecord WildNode [segment children param wild catch]
  Lookup
  (lookup [this path params]
    #_(println "w=>" segment "..." path)
    (let [i (.indexOf ^String path "/")]
      (if (pos? i)
        (let [value (subs path 0 i)]
          (let [childs [(impl/fast-get children (char-key path (inc i))) wild catch]
                path' (subs path (inc i))
                params (assoc params param value)]
            (some #(lookup % path' params) childs)))
        (assoc params param path)))))

(defrecord CatchAllNode [segment children param]
  Lookup
  (lookup [this path params]
    (assoc params param path)))

(defrecord StaticNode [^String segment ^Integer size children wild catch]
  Lookup
  (lookup [this path params]
    #_(println "s=>" segment "..." path)
    (if (.equals segment path)
      params
      (let [p (if (>= (count path) size) (subs path 0 size))]
        (if (.equals segment p)
          (let [childs [(impl/fast-get children (char-key path size)) wild catch]
                path (subs path size)]
            (some #(lookup % path params) childs)))))))

(defn- wild? [s]
  (contains? #{\: \*} (first s)))

(defn- wild-param?
  "Return true if a string segment starts with a wildcard string."
  [segment]
  (= \: (first segment)))

(defn- catch-all-param?
  "Return true if a string segment starts with a catch-all string."
  [segment]
  (= \* (first segment)))

(defn partition-wilds
  "Given a path-spec string, return a seq of strings with wildcards
  and catch-alls separated into their own strings. Eats the forward
  slash following a wildcard."
  [path-spec]
  (let [groups (partition-by wild? (str/split path-spec #"/"))
        first-groups (butlast groups)
        last-group (last groups)]
    (flatten
      (conj (mapv #(if (wild? (first %))
                     %
                     (str (str/join "/" %) "/"))
                  first-groups)
            (if (wild? (first last-group))
              last-group
              (str/join "/" last-group))))))

(defn contains-wilds?
  "Return true if the given path-spec contains any wildcard params or
  catch-alls."
  [path-spec]
  (let [parts (partition-wilds path-spec)]
    (or (> (count parts) 1)
        (wild? (first parts)))))

(defn- make-node
  "Given a path-spec segment string and a payload object, return a new
  tree node."
  [segment o]
  (cond
    (wild-param? segment)
    (map->WildNode
      {:segment segment
       :param (keyword (subs segment 1))})

    (catch-all-param? segment)
    (map->CatchAllNode
      {:segment segment
       :param (keyword (subs segment 1))})

    :else
    (map->StaticNode
      {:segment segment})))

(defn- add-child
  "Given a tree node, a single char string key and a child node,
  return a new tree where this node has this child."
  [node key child]
  (assoc-in node [:children key] child))

(declare insert)

(defn- insert-child
  "Given a tree node, a single char string key, a path-spec string and
  a payload object, return a tree where this object has been instered
  at path-spec under this node."
  [node key path-spec o]
  (update-in node [:children key] insert path-spec o))

(defn- new-node
  "Given a path-spec and a payload object, return a new tree node. If
  the path-spec contains wildcards or catch-alls, will return parent
  node of a tree (linked list)."
  [path-spec o]
  (if (contains-wilds? path-spec)
    (let [parts (partition-wilds path-spec)]
      (reduce (fn [child segment]
                (when (catch-all-param? segment)
                  (throw (ex-info "catch-all may only appear at the end of a path spec"
                                  {:patch-spec path-spec})))
                (-> (make-node segment nil)
                    (add-child (subs (:segment child) 0 1) child)))
              (let [segment (last parts)]
                (make-node segment o))
              (reverse (butlast parts))))
    (make-node path-spec o)))

(defn- calc-lcs
  "Given two strings, return the end index of the longest common
  prefix string."
  [s1 s2]
  (loop [i 1]
    (cond (or (< (count s1) i)
              (< (count s2) i))
          (dec i)

          (= (subs s1 0 i)
             (subs s2 0 i))
          (recur (inc i))

          :else (dec i))))

(defn- split
  "Given a node, a path-spec, a payload object to insert into the tree
  and the lcs, split the node and return a new parent node with the
  old contents of node and the new item as children.
  lcs is the index of the longest common string in path-spec and the
  segment of node."
  [node path-spec o lcs]
  (let [segment (:segment node)
        common (subs path-spec 0 lcs)
        parent (new-node common nil)]
    (if (= common path-spec)
      (-> parent
          (add-child (char-key segment lcs)
                     (update-in node [:segment] subs lcs)))
      (-> parent
          (add-child (char-key segment lcs)
                     (update-in node [:segment] subs lcs))
          (insert-child (char-key path-spec lcs) (subs path-spec lcs) o)))))

(defn insert
  "Given a tree node, a path-spec and a payload object, return a new
  tree with payload inserted."
  [node path-spec o]
  (let [segment (:segment node)]
    (cond (nil? node)
          (new-node path-spec o)

          (= segment path-spec)
          node

          ;; handle case where path-spec is a wildcard param
          (wild-param? path-spec)
          (let [lcs (calc-lcs segment path-spec)
                common (subs path-spec 0 lcs)]
            (if (= common segment)
              (let [path-spec (subs path-spec (inc lcs))]
                (insert-child node (subs path-spec 0 1) path-spec o))
              (throw (ex-info "route conflict"
                              {:node node
                               :path-spec path-spec
                               :segment segment}))))

          ;; in the case where path-spec is a catch-all, node should always be nil.
          ;; getting here means we have an invalid route specification
          (catch-all-param? path-spec)
          (throw (ex-info "route conflict"
                          {:node node
                           :path-spec path-spec
                           :segment segment}))

          :else
          (let [lcs (calc-lcs segment path-spec)]
            (cond (= lcs (count segment))
                  (insert-child node (char-key path-spec lcs) (subs path-spec lcs) o)

                  :else
                  (split node path-spec o lcs))))))

(defn optimize [tree]
  (walk/postwalk
    (fn [x]
      (if (or (instance? StaticNode x)
              (instance? WildNode x))
        (let [wild-child (get-in x [:children ":"])
              catch-all-child (get-in x [:children "*"])]
          (cond-> x
                  wild-child (-> (assoc :wild wild-child)
                                 (update :children dissoc ":"))
                  catch-all-child (-> (assoc :catch catch-all-child)
                                      (update :children dissoc "*"))
                  (:segment x) (assoc :size (-> x :segment count))
                  true (update :children impl/fast-map)))
        x))
    tree))

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

(comment
  (require '[io.pedestal.http.route.prefix-tree :as p])
  (def tree-old (reduce (fn [acc [p d]] (p/insert acc p d)) nil routes))

  ;; 2.3ms
  (cc/quick-bench (dotimes [_ 1000] (p/lookup tree-old "/v1/orgs/1/topics"))))

(comment
  (def tree-new (optimize (reduce (fn [acc [p d]] (insert acc p d)) nil routes)))

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
  (cc/quick-bench (dotimes [_ 1000] (lookup tree-new "/v1/orgs/1/topics" {}))))
