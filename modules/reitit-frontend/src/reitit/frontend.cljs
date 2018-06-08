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

(defn query-string
  "Given map, creates "
  [m]
  (str/join "&" (map (fn [[k v]]
                       (str (js/encodeURIComponent (name k))
                            "="
                            ;; FIXME: create protocol to handle how types are converted to string
                            ;; FIXME: array to multiple params
                            (if (coll? v)
                              (str/join "," (map #(js/encodeURIComponent %) v))
                              (js/encodeURIComponent v))))
                     m)))

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
                          :path (:param match)})]
        (assoc match :parameters parameters)))))

(defn match-by-name
  [router name params]
  ;; FIXME: move router not initialized to re-frame integration?
  (if router
    (or (reitit/match-by-name router name params)
        ;; FIXME: return nil?
        (do
          (js/console.error "Can't create URL for route " (pr-str name) (pr-str params))
          nil))
    ::not-initialized))
