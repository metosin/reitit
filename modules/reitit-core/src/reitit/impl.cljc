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

(defn wild? [s]
  (contains? #{\: \*} (first (str s))))

(defn catch-all? [s]
  (= \* (first (str s))))

(defn wild-param [s]
  (let [ss (str s)]
    (if (= \: (first ss))
      (keyword (subs ss 1)))))

(defn catch-all-param [s]
  (let [ss (str s)]
    (if (= \* (first ss))
      (keyword (subs ss 1)))))

(defn wild-or-catch-all-param? [x]
  (boolean (or (wild-param x) (catch-all-param x))))

(defn segments [path]
  #?(:clj  (.split ^String path "/" 666)
     :cljs (.split path #"/" 666)))

(defn contains-wilds? [path]
  (boolean (some wild-or-catch-all-param? (segments path))))

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

(defn wild-route? [[path]]
  (contains-wilds? path))

(defn conflicting-routes? [[p1] [p2]]
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

(defn strip-nils [m]
  (->> m (remove (comp nil? second)) (into {})))
