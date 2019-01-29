(ns reitit.trie
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str])
  (:import [reitit Trie Trie$Match Trie$Matcher]))

(defrecord Match [data path-params])
(defrecord Node [children wilds catch-all data])

;; https://stackoverflow.com/questions/8033655/find-longest-common-prefix
(defn- -common-prefix [s1 s2]
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

(defn- -split [s]
  (let [-static (fn [from to] (if-not (= from to) [(subs s from to)]))
        -wild (fn [from to] [(-keyword (subs s (inc from) to))])
        -catch-all (fn [from to] [#{(keyword (subs s (inc from) to))}])]
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

(defn- -node [m]
  (map->Node (merge {:children {}, :wilds {}, :catch-all {}} m)))

(defn- -insert [node [path & ps] data]
  (let [node' (cond

                (nil? path)
                (assoc node :data data)

                (keyword? path)
                (update-in node [:wilds path] (fn [n] (-insert (or n (-node {})) ps data)))

                (set? path)
                (assoc-in node [:catch-all path] (-node {:data data}))

                (str/blank? path)
                (-insert node ps data)

                :else
                (or
                  (reduce
                    (fn [_ [p n]]
                      (if-let [cp (-common-prefix p path)]
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
      (-> (merge-with merge node' child)
          (update :children dissoc ""))
      node')))

(defn insert [node path data]
  (-insert (or node (-node {})) (-split path) data))

(defn ^Trie$Matcher compile [{:keys [data children wilds catch-all]}]
  (let [matchers (cond-> []
                         data (conj (Trie/dataMatcher data))
                         children (into (for [[p c] children] (Trie/staticMatcher p (compile c))))
                         wilds (into (for [[p c] wilds] (Trie/wildMatcher p (compile c))))
                         catch-all (into (for [[p c] catch-all] (Trie/catchAllMatcher (first p) (:data c)))))]
    (if (rest matchers)
      (Trie/linearMatcher matchers)
      (first matchers))))

(defn pretty [{:keys [data children wilds catch-all]}]
  (into
    (if data [data] [])
    (mapcat (fn [[p n]] [p (pretty n)]) (concat children wilds catch-all))))

(defn lookup [^Trie$Matcher matcher path]
  (if-let [match ^Trie$Match (Trie/lookup matcher ^String path)]
    (->Match (.data match) (.parameters match))))

(defn scanner [compiled-tries]
  (Trie/scanner compiled-tries))

;;
;; matcher
;;

;;
;; spike
;;

(-> nil
    (insert "/:abba" 1)
    (insert "/kikka" 2)
    (insert "/kikka/kakka/kukka" 3)
    (insert "/kikka/:kakka/kukka" 4)
    (insert "/kikka/kuri/{user/doc}.html" 5)
    (insert "/kikkare/*path" 6)
    #_(pretty))

(-> nil
    (insert "/kikka" 2)
    (insert "/kikka/kakka/kukka" 3)
    (insert "/kikka/:kakka/kurkku" 4)
    (insert "/kikka/kuri/{user/doc}/html" 5)
    (compile)
    (lookup "/kikka/kakka/kurkku"))

;; =>

["/"
 ["kikka" [2
           "/" ["k" ["akka/kukka" [3]
                     "uri/" [:user/doc [".html" [5]]]]
                :kakka ["/kukka" [4]]]
           "re/" [#{:path} [6]]]
  :abba [1]]]


