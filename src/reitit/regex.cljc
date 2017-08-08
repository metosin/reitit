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

(ns reitit.regex
  (:require [clojure.string :as str])
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

(defn- wild? [s]
  (contains? #{\: \*} (first s)))

(defn- partition-wilds
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
;; Routing
;;

(defn matcher [path]
  (if (contains-wilds? path)
    (as-> (parse-path path) $
          (assoc $ :path-re (path-regex $))
          (path-matcher $))
    #(if (= path %) {})))
