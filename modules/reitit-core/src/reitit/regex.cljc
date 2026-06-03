(ns reitit.regex
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [reitit.core :as r])
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
     :cljs (.-source regex-obj)))

(defn regex-quote
  "Escapes a string for regex in both Clojure and ClojureScript."
  [s]
  #?(:clj (Pattern/quote s)
     :cljs (str/replace s #"[.*+?^${}\(\)|\[\]\\]" "\\$&")))

(defn- regex-param-segment? [segment]
  (str/starts-with? segment ":#"))

(defn- param-segment? [segment]
  (str/starts-with? segment ":"))

(defn- param-key [segment]
  (keyword (subs segment (if (regex-param-segment? segment) 2 1))))

(defn- string->regex [s]
  (try
    (re-pattern s)
    (catch #?(:clj Exception :cljs js/Error) _
      nil)))

(defn- ->regex [x]
  (cond
    (regex? x) x
    (string? x) (string->regex x)))

(defn- path-regex [route-data param-key]
  (let [path-regex (:path-regex route-data)
        param-value (get path-regex param-key)]
    (when (contains? path-regex param-key)
      (->regex param-value))))

(defn- legacy-path-regex [route-data param-key]
  (let [param-regex (get-in route-data [:parameters :path param-key])]
    (when (regex? param-regex)
      param-regex)))

(defn compile-regex-route
  "Given a route vector [path route-data], returns a map with:
   - :pattern: a compiled regex pattern built from the path segments,
   - :group-keys: vector of parameter keys in order,
   - :route-data: the provided route data,
   - :result: compiled route result, when provided,
   - :original-segments: original path segments for path generation,
   - :template: the original path template for Match objects."
  [[path route-data result]]
  (let [;; Normalize route-data to ensure it's a map with :name
        route-data (if (keyword? route-data)
                     {:name route-data}
                     route-data)

        ;; Store the original path template for Match objects
        template (if (str/starts-with? path "/")
                   path
                   (str "/" path))

        ;; Handle paths with or without leading slashes
        normalized-path (cond-> path
                                (str/starts-with? path "/") (subs 1))

        ;; Split into segments, handling empty paths
        segments (if (empty? normalized-path)
                   []
                   (str/split normalized-path #"/"))

        ;; Store original segments for path generation
        original-segments segments

        compiled-segments
        (map (fn [seg]
               (cond
                 (regex-param-segment? seg)
                 (let [param-key (param-key seg)
                       param-regex (path-regex route-data param-key)]
                   (when-not param-regex
                     (throw (ex-info (str "Missing path regex for " seg " in " (:name route-data))
                                     {:segment seg
                                      :name (:name route-data)})))
                   (str "(" (pattern-str param-regex) ")"))

                 (param-segment? seg)
                 (if-let [param-regex (legacy-path-regex route-data (param-key seg))]
                   (str "(" (pattern-str param-regex) ")")
                   "([^/]+)")

                 :else
                 (regex-quote seg)))
             segments)

        ;; Create the pattern string, handling special case for root path
        pattern-string (if (empty? segments)
                         "^/?$"  ;; Match root path with optional trailing slash
                         (str "^/" (str/join "/" compiled-segments) "$"))

        group-keys (->> segments
                        (filter param-segment?)
                        (map param-key)
                        (vec))]

    {:pattern (re-pattern pattern-string)
     :group-keys group-keys
     :route-data route-data
     :result result
     :original-segments original-segments
     :template template}))

(defn- generate-path
  "Generate a path from a route and path parameters."
  [route path-params]
  (if (empty? (:original-segments route))
    "/"
    (str "/" (str/join "/"
                       (map (fn [segment]
                              (if (param-segment? segment)
                                (get path-params (param-key segment) "")
                                segment))
                            (:original-segments route))))))

(defrecord RegexRouter [compiled-routes opts]
  r/Router
  (router-name [_] :regex-router)

  (routes [_]
    (mapv (fn [{:keys [route-data original-segments]}]
            [(str "/" (str/join "/" original-segments)) route-data])
          compiled-routes))

  (compiled-routes [_] compiled-routes)

  (options [_] opts)

  (route-names [_]
    (keep (comp :name :route-data) compiled-routes))

  (match-by-path [_ path]
    (some (fn [{:keys [pattern group-keys route-data result template]}]
            (when-let [matches (re-matches pattern path)]
              (let [params (zipmap group-keys (rest matches))]
                (r/->Match template route-data result params path))))
          compiled-routes))

  (match-by-name [this name]
    (r/match-by-name this name {}))

  (match-by-name [router name path-params]
    (when-let [{:keys [group-keys route-data result template] :as route}
               (first (filter #(= name (get-in % [:route-data :name])) (r/compiled-routes router)))]
      ;; Check if all required params are provided
      (let [required-params (set group-keys)
            provided-params (set (keys path-params))]
        (if (every? #(contains? provided-params %) required-params)
          ;; All required params provided, return a Match
          (let [path (generate-path route path-params)]
            (r/->Match template route-data result path-params path))
          ;; Some required params missing, return a PartialMatch
          (let [missing (set/difference required-params provided-params)]
            (r/->PartialMatch template route-data result path-params missing)))))))

(defn regex-router
  "Create a RegexRouter from a vector of routes.
   Each route should be a vector [path route-data]."
  ([routes]
   (regex-router routes {}))
  ([routes opts]
   (->RegexRouter (mapv compile-regex-route routes) opts)))

(defn create-regex-router
  "Create a RegexRouter from a vector of routes.
   Each route should be a vector [path route-data]."
  [routes]
  (regex-router routes))
