(ns reitit.frontend
  ""
  (:require [reitit.core :as reitit]
            [clojure.string :as str]
            [reitit.coercion :as coercion]
            [goog.events :as e]
            [goog.dom :as dom])
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
            ;; Return uncoerced values if coercion is not enabled - so
            ;; that tha parameters are always accessible from same property.
            ;; FIXME: coerce! can't be used as it doesn't take query-params
            parameters (if (:result match)
                         (coercion/coerce-request (:result match) {:query-params q
                                                                   :path-params (:path-params match)})
                         {:query q
                          :path (:path-params match)})]
        (assoc match :parameters parameters)))))

(defn match-by-name
  ([router name]
   (match-by-name router name {}))
  ([router name path-params]
   (reitit/match-by-name router name path-params)))
