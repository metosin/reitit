(ns reitit.frontend
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [reitit.coercion :as coercion]
            [reitit.coercion :as rc]
            [reitit.core :as r])
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
  coerced parameters. Returns nil if no match found."
  [router path]
  (let [uri (.parse Uri path)
        path (.getPath uri)]
    (if-let [match (or (r/match-by-path router path)
                       (if-let [trailing-slash-handling (:trailing-slash-handling (r/options router))]
                         ;; TODO: Maybe the original path should be added under some key in match,
                         ;; so it is easy for user to see if trailing slash "redirect" happened?
                         (if (str/ends-with? path "/")
                           (if (not= trailing-slash-handling :add)
                             (r/match-by-path router (subs path 0 (dec (count path)))))
                           (if (not= trailing-slash-handling :remove)
                             (r/match-by-path router (str path "/"))))))]
      ;; User can update browser location in on-navigate call using replace-state
      (let [q (query-params uri)
            match (assoc match :query-params q)
            ;; Return uncoerced values if coercion is not enabled - so
            ;; that tha parameters are always accessible from same property.
            parameters (or (coercion/coerce! match)
                           {:path (:path-params match)
                            :query q})]
        (assoc match :parameters parameters)))))

(defn match-by-name
  "Given a router, route name and optionally path-parameters,
  will return a Match (exact match), PartialMatch (missing path-parameters)
  or `nil` (no match)."
  ([router name]
   (match-by-name router name {}))
  ([router name path-params]
   (r/match-by-name router name path-params)))

(defn router
  "Create a `reitit.core.router` from raw route data and optionally an options map.
  Enables request coercion. See [[reitit.core/router]] for details on options.

  Additional options:

  | key          | description |
  | -------------|-------------|
  | :trailing-slash-handling | TODO |
  "
  ([raw-routes]
   (router raw-routes {}))
  ([raw-routes opts]
   (r/router raw-routes (merge {:compile rc/compile-request-coercers} opts))))

(defn match-by-name!
  "Logs problems using console.warn"
  ([router name]
   (match-by-name! router name {}))
  ([router name path-params]
   (if-let [match (match-by-name router name path-params)]
     (if (r/partial-match? match)
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
