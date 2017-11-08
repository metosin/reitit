(ns reitit.trie
  (:require [reitit.impl :as impl]))

;;
;; original https://github.com/pedestal/pedestal/blob/master/route/src/io/pedestal/http/route/prefix_tree.clj
;;

(declare insert)

(defn- char-key [s i]
  (if (< i (count s))
    (subs s i (inc i))))

(defn- maybe-wild-node [children]
  (get children ":"))

(defn- maybe-catch-all-node [children]
  (get children "*"))

(defprotocol Node
  (lookup [this path params])
  (get-segment [this])
  (update-segment [this subs lcs])
  (set-data [this data])
  (add-child [this key child])
  (insert-child [this key path-spec data]))

(extend-protocol Node
  nil
  (lookup [_ _ _])
  (get-segment [_]))

(defrecord Match [data params])

(defn- wild-node [segment param children data]
  (let [?wild (maybe-wild-node children)
        ?catch (maybe-catch-all-node children)
        children' (impl/fast-map children)]
    (reify
      Node
      (lookup [_ path params]
        (let [i (.indexOf ^String path "/")]
          (if (pos? i)
            (let [value (subs path 0 i)]
              (let [child (impl/fast-get children' (char-key path (inc i)))
                    path' (subs path (inc i))
                    params (assoc params param value)]
                (or (lookup child path' params)
                    (lookup ?wild path' params)
                    (lookup ?catch path' params))))
            (->Match data (assoc params param path)))))
      (get-segment [_]
        segment)
      (set-data [_ data]
        (wild-node segment param children data))
      (add-child [_ key child]
        (wild-node segment param (assoc children key child) data))
      (insert-child [_ key path-spec child-data]
        (wild-node segment param (update children key insert path-spec child-data) data)))))

(defn- catch-all-node [segment children param data]
  (reify
    Node
    (lookup [_ path params]
      (->Match data (assoc params param path)))
    (get-segment [_]
      segment)))

(defn- static-node [^String segment children data]
  (let [size (count segment)
        ?wild (maybe-wild-node children)
        ?catch (maybe-catch-all-node children)
        children' (impl/fast-map children)]
    (reify
      Node
      (lookup [_ path params]
        (if (#?(:clj .equals, :cljs =) segment path)
          (->Match data params)
          (let [p (if (>= (count path) size) (subs path 0 size))]
            (if (#?(:clj .equals, :cljs =) segment p)
              (let [child (impl/fast-get children' (char-key path size))
                    path (subs path size)]
                (or (lookup child path params)
                    (lookup ?wild path params)
                    (lookup ?catch path params)))))))
      (get-segment [_]
        segment)
      (update-segment [_ subs lcs]
        (static-node (subs segment lcs) children data))
      (set-data [_ data]
        (static-node segment children data))
      (add-child [_ key child]
        (static-node segment (assoc children key child) data))
      (insert-child [_ key path-spec child-data]
        (static-node segment (update children key insert path-spec child-data) data)))))

(defn- make-node
  "Given a path-spec segment string and a payload object, return a new
  tree node."
  [segment data]
  (cond
    (impl/wild-param? segment)
    (wild-node segment (keyword (subs segment 1)) nil data)

    (impl/catch-all-param? segment)
    (catch-all-node segment (keyword (subs segment 1)) nil data)

    :else
    (static-node segment nil data)))

(defn- new-node
  "Given a path-spec and a payload object, return a new tree node. If
  the path-spec contains wildcards or catch-alls, will return parent
  node of a tree (linked list)."
  [path-spec data]
  (if (impl/contains-wilds? path-spec)
    (let [parts (impl/partition-wilds path-spec)]
      (reduce (fn [child segment]
                (when (impl/catch-all-param? segment)
                  (throw (ex-info "catch-all may only appear at the end of a path spec"
                                  {:patch-spec path-spec})))
                (-> (make-node segment nil)
                    (add-child (subs (get-segment child) 0 1) child)))
              (let [segment (last parts)]
                (make-node segment data))
              (reverse (butlast parts))))
    (make-node path-spec data)))

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
  [node path-spec data lcs]
  (let [segment (get-segment node)
        common (subs path-spec 0 lcs)
        parent (new-node common nil)]
    (if (= common path-spec)
      (-> (set-data parent data)
          (add-child (char-key segment lcs) (update-segment node subs lcs)))
      (-> parent
          (add-child (char-key segment lcs) (update-segment node subs lcs))
          (insert-child (char-key path-spec lcs) (subs path-spec lcs) data)))))

(defn insert
  "Given a tree node, a path-spec and a payload object, return a new
  tree with payload inserted."
  [node path-spec data]
  (let [segment (get-segment node)]
    (cond (nil? node)
          (new-node path-spec data)

          (= segment path-spec)
          (set-data node data)

          ;; handle case where path-spec is a wildcard param
          (impl/wild-param? path-spec)
          (let [lcs (calc-lcs segment path-spec)
                common (subs path-spec 0 lcs)]
            (if (= common segment)
              (let [path-spec (subs path-spec (inc lcs))]
                (insert-child node (subs path-spec 0 1) path-spec data))
              (throw (ex-info "route conflict"
                              {:node node
                               :path-spec path-spec
                               :segment segment}))))

          ;; in the case where path-spec is a catch-all, node should always be nil.
          ;; getting here means we have an invalid route specification
          (impl/catch-all-param? path-spec)
          (throw (ex-info "route conflict"
                          {:node node
                           :path-spec path-spec
                           :segment segment}))

          :else
          (let [lcs (calc-lcs segment path-spec)]
            (cond (= lcs (count segment))
                  (insert-child node (char-key path-spec lcs) (subs path-spec lcs) data)

                  :else
                  (split node path-spec data lcs))))))
