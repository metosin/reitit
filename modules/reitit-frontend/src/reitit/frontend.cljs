(ns reitit.frontend
  (:require [clojure.set :as set]
            [reitit.coercion :as coercion]
            [reitit.core :as r]
            goog.Uri
            goog.Uri.QueryData))

(defn- query-param [^goog.uri.QueryData q k]
  (let [vs (.getValues q k)]
    (if (< (alength vs) 2)
      (aget vs 0)
      (vec vs))))

(defn query-params
  "Given goog.Uri, read query parameters into a Clojure map."
  [^goog.Uri uri]
  (let [q (.getQueryData uri)]
    (->> q
         (.getKeys)
         (map (juxt keyword #(query-param q %)))
         (into {}))))

(defn set-query-params
  "Given Reitit-frontend path, update the query params
  with given function and arguments.

  Note: coercion is not applied to the query params"
  [path new-query-or-update-fn]
  (let [^goog.Uri uri (goog.Uri/parse path)
        new-query (if (fn? new-query-or-update-fn)
                    (new-query-or-update-fn (query-params uri))
                    new-query-or-update-fn)]
    ;; NOTE: Differences to reitit.impl/query-string?
    ;; reitit fn adds "=" even if value is empty string
    ;; reitit encodes " " as "+" while browser and goog.Uri encode as "%20"
    (.setQueryData uri (goog.Uri.QueryData/createFromMap (clj->js new-query)))
    (.toString uri)))

(defn match-by-path
  "Given routing tree and current path, return match with possibly
  coerced parameters. Return nil if no match found.

  :on-coercion-error - a sideeffecting fn of `match exception -> nil`"
  ([router path] (match-by-path router path nil))
  ([router path {:keys [on-coercion-error]}]
   (let [uri (.parse goog.Uri path)
         coerce! (if on-coercion-error
                   (fn [match]
                     (try (coercion/coerce! match)
                          (catch js/Error e
                            (on-coercion-error match e)
                            (throw e))))
                   coercion/coerce!)]
     (if-let [match (r/match-by-path router (.getPath uri))]
       (let [q (query-params uri)
             fragment (when (.hasFragment uri)
                        (.getFragment uri))
             match (assoc match
                          :query-params q
                          :fragment fragment)
             ;; Return uncoerced values if coercion is not enabled - so
             ;; that tha parameters are always accessible from same property.
             parameters (or (coerce! match)
                            {:path (:path-params match)
                             :query q
                             :fragment fragment})]
         (assoc match :parameters parameters))))))

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
  Enables request coercion. See [[reitit.core/router]] for details on options."
  ([raw-routes]
   (router raw-routes {}))
  ([raw-routes opts]
   (r/router raw-routes (merge {:compile coercion/compile-request-coercers} opts))))

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
