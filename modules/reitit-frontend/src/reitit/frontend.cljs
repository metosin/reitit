(ns reitit.frontend
  ""
  (:require [reitit.core :as reitit]
            [clojure.set :as set]
            [reitit.coercion :as coercion])
  (:import goog.Uri))

(defn query-params
  "Given goog.Uri, read query parameters into Clojure map."
  [^goog.Uri uri]
  (let [q (.getQueryData uri)]
    (->> q
         (.getKeys)
         (map (juxt keyword #(.get q %)))
         (into {}))))

(defn match-by-path
  "Given routing tree and current path, return match with possibly
  coerced parameters. Return nil if no match found."
  [router path]
  (let [uri (.parse Uri path)]
    (if-let [match (reitit/match-by-path router (.getPath uri))]
      (let [q (query-params uri)
            match (assoc match :query-params q)
            ;; Return uncoerced values if coercion is not enabled - so
            ;; that tha parameters are always accessible from same property.
            parameters (or (coercion/coerce! match)
                           {:path (:path-params match)
                            :query q})]
        (assoc match :parameters parameters)))))

(defn match-by-name
  ([router name]
   (match-by-name router name {}))
  ([router name path-params]
   (reitit/match-by-name router name path-params)))

(defn match-by-name!
  "Logs problems using console.warn"
  ([router name]
   (match-by-name! router name {}))
  ([router name path-params]
   (if-let [match (match-by-name router name path-params)]
     (if (reitit/partial-match? match)
       (if (every? #(contains? path-params %) (:required match))
         match
         (let [defined (-> path-params keys set)
               missing (set/difference (:required match) defined)]
           (js/console.warn
             "missing path-params for route" name
             {:template (:template match)
              :missing missing
              :path-params path-params
              :required (:required match)})
           nil))
       match)
     (do (js/console.warn "missing route" name)
         nil))))
