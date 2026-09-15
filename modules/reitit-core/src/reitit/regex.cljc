(ns reitit.regex
  "A reitit Router implementation that matches paths with regular expressions.

   Path segments support the standard reitit syntaxes plus a regex-constrained
   form:
   - `:name` / `{name}`   wildcard, matches a single path segment.
   - `:#name`             regex-constrained segment; the pattern is looked up in
                          the route-data under `[:path-regex name]` (a regex or
                          a string), and must be present or compilation throws.
   - `*name` / `{*name}`  catch-all, matches the rest of the path, including `/`.
   - anything else        a static segment, matched literally.

   Matching is linear and first-match-wins, in declaration order. Order routes
   from most to least specific; place broad regex/catch-all matchers after the
   specific routes they would otherwise shadow.

   Path generation: a `:#` regex param's value is emitted verbatim; its regex
   is the contract for what is valid, and may legitimately allow `/`, `@`, etc.
   Bare wildcard segments are percent-encoded; on match, params are URL-decoded.

   Coercion: this router threads reitit's compiled `:result` through to every
   Match, so request coercion works exactly as with the built-in routers when
   the router is built with the same `:compile` option."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [reitit.core :as r]
            [reitit.impl :as impl])
  #?(:clj (:import [java.util.regex Pattern])))

(defn regex?
  "Returns true if x is a regex pattern in both Clojure and ClojureScript."
  [x]
  #?(:clj (instance? Pattern x)
     :cljs (instance? js/RegExp x)))

(defn pattern-str
  "Gets the pattern string from a regex in both Clojure and ClojureScript."
  [regex-obj]
  #?(:clj (.pattern ^Pattern regex-obj)
     :cljs (str (.-source regex-obj))))

(defn regex-quote
  "Escapes a string for regex in both Clojure and ClojureScript."
  [s]
  #?(:clj (Pattern/quote s)
     :cljs (str/replace s #"[.*+?^${}\(\)|\[\]\\]" "\\$&")))

(defn- count-capturing-groups
  "Number of capturing groups contained in the regex pattern string. Used to
   keep the combined route pattern's group indices aligned with the parameter
   list even when a user-supplied regex contains its own capturing groups."
  [pattern-string]
  #?(:clj (.groupCount (.matcher (re-pattern pattern-string) ""))
     ;; Appending an empty alternative forces a match against "", so .exec
     ;; always returns an array whose length is capturing-groups + 1.
     :cljs (dec (.-length (.exec (js/RegExp. (str pattern-string "|")) "")))))

;; Characters that are legal inside a single URL path segment per RFC 3986
;; (pchar = unreserved / sub-delims / ":" / "@"). Anything outside this set is
;; percent-encoded when generating a path. `:#` regex params are emitted verbatim
;; because their route regex is the validity contract.
(def ^:private path-segment-unsafe-re #"[^A-Za-z0-9!$&'()*+,;=:@._~-]+")

(defn- url-encode-segment
  "Percent-encodes `s` for use within a single URL path segment, leaving
   path-legal characters, including `@`, intact. Returns nil for nil."
  [s]
  (when s
    (str/replace s path-segment-unsafe-re
                 (fn [m] #?(:clj (impl/percent-encode m)
                            :cljs (js/encodeURIComponent m))))))

(defn- param-key
  "The keyword name from a parameter segment, stripping the syntax prefix."
  [seg]
  (cond
    (str/starts-with? seg ":#") (keyword (subs seg 2))
    (str/starts-with? seg "*") (keyword (subs seg 1))
    (and (str/starts-with? seg "{*") (str/ends-with? seg "}")) (keyword (subs seg 2 (dec (count seg))))
    (and (str/starts-with? seg "{") (str/ends-with? seg "}")) (keyword (subs seg 1 (dec (count seg))))
    (str/starts-with? seg ":") (keyword (subs seg 1))))

(defn- parse-segment
  "Classifies a raw path segment into a parsed segment map. Supported forms:
   - `:name`        wildcard, matching a single path segment
   - `{name}`       wildcard, bracket syntax
   - `:#name`       regex-constrained segment from route-data `:path-regex`
   - `*name`        catch-all matching the rest of the path, including slashes
   - `{*name}`      catch-all, bracket syntax
   - anything else  static segment, matched literally"
  [seg]
  (cond
    (str/starts-with? seg ":#")
    {:type :regex :key (param-key seg) :raw seg}

    (or (str/starts-with? seg "*")
        (and (str/starts-with? seg "{*") (str/ends-with? seg "}")))
    {:type :catch-all :key (param-key seg) :raw seg}

    (or (str/starts-with? seg ":")
        (and (str/starts-with? seg "{") (str/ends-with? seg "}")))
    {:type :wild :key (param-key seg) :raw seg}

    :else
    {:type :static :raw seg}))

(defn- ->regex [x]
  (cond
    (regex? x) x
    (string? x) (try
                  (re-pattern x)
                  (catch #?(:clj Exception :cljs js/Error) _
                    nil))))

(defn- path-regex-pattern
  "Resolves the regex pattern string for a `:#name` segment from route-data."
  [{:keys [key raw]} route-data]
  (let [path-regex (get route-data :path-regex)
        param-regex (->regex (get path-regex key))]
    (when-not param-regex
      (throw (ex-info (str "Missing path regex for " raw " in " (:name route-data))
                      {:segment raw
                       :name (:name route-data)})))
    (pattern-str param-regex)))

(defn- legacy-path-regex-pattern
  [{:keys [key]} route-data]
  (when-let [param-regex (->regex (get-in route-data [:parameters :path key]))]
    (pattern-str param-regex)))

(defn- param-fragment [pattern-string]
  {:fragment (str "(" pattern-string ")")
   :inner-groups (count-capturing-groups pattern-string)})

(defn compile-regex-route
  "Given a route `[path route-data]` or `[path route-data result]` as produced
   by reitit's route compilation, returns a map with:
   - `:pattern`     a compiled regex pattern built from the path segments,
   - `:params`      vector of `{:key :group :type}` for each parameter segment,
                    where `:group` is the capture-group index in `:pattern`,
   - `:segments`    parsed segments, used for path generation,
   - `:route-data`  the provided route data,
   - `:result`      the compiled route result threaded through,
   - `:template`    the original path template for Match objects."
  [[path route-data result]]
  (let [route-data (if (keyword? route-data)
                     {:name route-data}
                     route-data)
        template (if (str/starts-with? path "/")
                   path
                   (str "/" path))
        normalized-path (cond-> path
                                (str/starts-with? path "/") (subs 1))
        segments (if (empty? normalized-path)
                   []
                   (str/split normalized-path #"/"))
        parsed (mapv parse-segment segments)
        {:keys [fragments params]}
        (reduce
         (fn [{:keys [fragments params group]} {:keys [type key] :as segment}]
           (case type
             :static
             {:fragments (conj fragments (regex-quote (:raw segment)))
              :params params
              :group group}

             :wild
             (if-let [pattern-string (legacy-path-regex-pattern segment route-data)]
               (let [{:keys [fragment inner-groups]} (param-fragment pattern-string)]
                 {:fragments (conj fragments fragment)
                  :params (conj params {:key key :group group :type type})
                  :group (+ group 1 inner-groups)})
               {:fragments (conj fragments "([^/]+)")
                :params (conj params {:key key :group group :type type})
                :group (inc group)})

             :catch-all
             {:fragments (conj fragments "(.*)")
              :params (conj params {:key key :group group :type type})
              :group (inc group)}

             :regex
             (let [{:keys [fragment inner-groups]} (param-fragment (path-regex-pattern segment route-data))]
               {:fragments (conj fragments fragment)
                :params (conj params {:key key :group group :type type})
                :group (+ group 1 inner-groups)})))
         {:fragments [] :params [] :group 1}
         parsed)
        pattern-string (if (empty? parsed)
                         "^/?$"
                         (str "^/" (str/join "/" fragments) "$"))]
    {:pattern (re-pattern pattern-string)
     :params params
     :segments parsed
     :route-data route-data
     :result result
     :template template}))

(defn- generate-path
  "Generate a path from a compiled route and path parameters."
  [{:keys [segments]} path-params]
  (if (empty? segments)
    "/"
    (str "/" (str/join "/"
                       (map (fn [{:keys [type key raw]}]
                              (let [v (impl/into-string (get path-params key ""))]
                                (case type
                                  :static raw
                                  :wild (url-encode-segment v)
                                  :regex v
                                  :catch-all (->> (str/split v #"/")
                                                  (map url-encode-segment)
                                                  (str/join "/")))))
                            segments)))))

(defrecord RegexRouter [compiled-routes opts]
  r/Router
  (router-name [_] :regex-router)

  (routes [_]
    (mapv (fn [{:keys [template route-data]}]
            [template route-data])
          compiled-routes))

  (compiled-routes [_] compiled-routes)

  (options [_] opts)

  (route-names [_]
    (into [] (keep (comp :name :route-data)) compiled-routes))

  (match-by-path [_ path]
    (some (fn [{:keys [pattern params route-data result template]}]
            (when-let [matches (re-matches pattern path)]
              (let [matches (if (vector? matches) matches [matches])
                    path-params (into {}
                                      (map (fn [{:keys [key group]}]
                                             [key (impl/url-decode (nth matches group nil))]))
                                      params)]
                (r/->Match template route-data result path-params path))))
          compiled-routes))

  (match-by-name [this name]
    (r/match-by-name this name {}))

  (match-by-name [_ name path-params]
    (when-let [{:keys [params route-data result template] :as route}
               (first (filter #(= name (get-in % [:route-data :name])) compiled-routes))]
      (let [required-params (set (map :key params))
            provided-params (set (keys path-params))]
        (if (every? #(contains? provided-params %) required-params)
          (let [path (generate-path route path-params)]
            (r/->Match template route-data result path-params path))
          (let [missing (set/difference required-params provided-params)]
            (r/->PartialMatch template route-data result path-params missing)))))))

(defn regex-router
  "Create a RegexRouter from a vector of routes.
   Each route should be `[path route-data]` or `[path route-data result]`."
  ([routes]
   (regex-router routes {}))
  ([routes opts]
   ;; Routes are matched linearly in declaration order: first match wins.
   (->RegexRouter (mapv compile-regex-route routes) opts)))

(defn create-regex-router
  "Create a RegexRouter from a vector of routes.
   Each route should be `[path route-data]`."
  [routes]
  (regex-router routes))
