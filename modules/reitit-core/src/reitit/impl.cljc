; Copyright 2013 Relevance, Inc.
; Copyright 2014-2016 Cognitect, Inc.

; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(ns ^:no-doc reitit.impl
  (:require [clojure.string :as str]
            [clojure.set :as set])
  (:import #?(:clj (java.util.regex Pattern))))

;;
;; https://github.com/pedestal/pedestal/blob/master/route/src/io/pedestal/http/route/path.clj
;;

(defn- parse-path-token [out string]
  (condp re-matches string
    #"^:(.+)$" :>> (fn [[_ token]]
                     (let [key (keyword token)]
                       (-> out
                           (update-in [:path-parts] conj key)
                           (update-in [:path-params] conj key)
                           (assoc-in [:path-constraints key] "([^/]+)"))))
    #"^\*(.+)$" :>> (fn [[_ token]]
                      (let [key (keyword token)]
                        (-> out
                            (update-in [:path-parts] conj key)
                            (update-in [:path-params] conj key)
                            (assoc-in [:path-constraints key] "(.*)"))))
    (update-in out [:path-parts] conj string)))

(defn- parse-path
  ([pattern] (parse-path {:path-parts [] :path-params [] :path-constraints {}} pattern))
  ([accumulated-info pattern]
   (if-let [m (re-matches #"/(.*)" pattern)]
     (let [[_ path] m]
       (reduce parse-path-token
               accumulated-info
               (str/split path #"/")))
     (throw (ex-info "Routes must start from the root, so they must begin with a '/'" {:pattern pattern})))))

;; TODO: is this correct?
(defn- re-quote [x]
  #?(:clj  (Pattern/quote x)
     :cljs (str/replace-all x #"([.?*+^$[\\]\\\\(){}|-])" "\\$1")))

(defn- path-regex [{:keys [path-parts path-constraints] :as route}]
  (let [[pp & pps] path-parts
        path-parts (if (and (seq pps) (string? pp) (empty? pp)) pps path-parts)]
    (re-pattern
      (apply str
             (interleave (repeat "/")
                         (map #(or (get path-constraints %) (re-quote %))
                              path-parts))))))

(defn- path-matcher [route]
  (let [{:keys [path-re path-params]} route]
    (fn [path]
      (when-let [m (re-matches path-re path)]
        (zipmap path-params (rest m))))))

;;
;; (c) https://github.com/pedestal/pedestal/blob/master/route/src/io/pedestal/http/route/prefix_tree.clj
;;

(defn wild? [s]
  (contains? #{\: \*} (first s)))

(defn wild-param?
  "Return true if a string segment starts with a wildcard string."
  [segment]
  (= \: (first segment)))

(defn catch-all-param?
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

;;
;; Routing (c) Metosin
;;

(defrecord Route [path matcher parts params data result])

(defn create [[path data result]]
  (let [path #?(:clj (.intern ^String path) :cljs path)]
    (as-> (parse-path path) $
          (assoc $ :path-re (path-regex $))
          (merge $ {:path path
                    :matcher (if (contains-wilds? path)
                               (path-matcher $)
                               #(if (#?(:clj .equals, :cljs =) path %) {}))
                    :result result
                    :data data})
          (dissoc $ :path-re :path-constraints)
          (update $ :path-params set)
          (set/rename-keys $ {:path-parts :parts
                              :path-params :params})
          (map->Route $))))

(defn segments [path]
  (let [ss (-> (str/split path #"/") rest vec)]
    (if (str/ends-with? path "/")
      (conj ss "") ss)))

(defn- catch-all? [segment]
  (= \* (first segment)))

(defn wild-route? [[path]]
  (contains-wilds? path))

(defn conflicting-routes? [[p1 :as route1] [p2 :as route2]]
  (loop [[s1 & ss1] (segments p1)
         [s2 & ss2] (segments p2)]
    (cond
      (= s1 s2 nil) true
      (or (nil? s1) (nil? s2)) false
      (or (catch-all? s1) (catch-all? s2)) true
      (or (wild? s1) (wild? s2)) (recur ss1 ss2)
      (not= s1 s2) false
      :else (recur ss1 ss2))))

(defn path-for [^Route route params]
  (if-let [required (:params route)]
    (if (every? #(contains? params %) required)
      (str "/" (str/join \/ (map #(get (or params {}) % %) (:parts route)))))
    (:path route)))

(defn throw-on-missing-path-params [template required params]
  (when-not (every? #(contains? params %) required)
    (let [defined (-> params keys set)
          missing (clojure.set/difference required defined)]
      (throw
        (ex-info
          (str "missing path-params for route " template " -> " missing)
          {:params params, :required required})))))

(defn fast-assoc
  #?@(:clj  [[^clojure.lang.Associative a k v] (.assoc a k v)]
      :cljs [[a k v] (assoc a k v)]))

(defn fast-map [m]
  #?@(:clj  [(java.util.HashMap. (or m {}))]
      :cljs [m]))

(defn fast-get
  #?@(:clj  [[^java.util.HashMap m k] (.get m k)]
      :cljs [[m k] (m k)]))
