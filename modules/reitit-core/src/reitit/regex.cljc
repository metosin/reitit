(ns reitit.regex
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [reitit.core :as r]))

(defn compile-regex-route
  "Given a route vector [path route-data], returns a map with:
   - :pattern: a compiled regex pattern built from the path segments,
   - :group-keys: vector of parameter keys in order,
   - :route-data: the provided route data,
   - :original-segments: original path segments for path generation,
   - :template: the original path template for Match objects."
  [[path route-data]]
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
               (if (str/starts-with? seg ":")
                 (let [param-key   (keyword (subs seg 1))
                       param-regex (get-in route-data [:parameters :path param-key])]
                   (if (and param-regex (instance? java.util.regex.Pattern param-regex))
                     (str "(" (.pattern ^java.util.regex.Pattern param-regex) ")")
                     ;; Fallback: match any non-slash characters.
                     "([^/]+)"))
                 (java.util.regex.Pattern/quote seg)))
             segments)

        ;; Create the pattern string, handling special case for root path
        pattern-str (if (empty? segments)
                      "^/?$"  ;; Match root path with optional trailing slash
                      (str "^/" (str/join "/" compiled-segments) "$"))

        group-keys (->> segments
                        (filter #(str/starts-with? % ":"))
                        (map #(keyword (subs % 1)))
                        (vec))]

    {:pattern    (re-pattern pattern-str)
     :group-keys group-keys
     :route-data route-data
     :original-segments original-segments
     :template template}))

(defn- generate-path
  "Generate a path from a route and path parameters."
  [route path-params]
  (if (empty? (:original-segments route))
    "/"
    (str "/" (str/join "/"
                       (map (fn [segment]
                              (if (str/starts-with? segment ":")
                                (let [param-key (keyword (subs segment 1))]
                                  (get path-params param-key ""))
                                segment))
                            (:original-segments route))))))

(defrecord RegexRouter [compiled-routes]
  r/Router
  (router-name [_] :regex-router)

  (routes [_]
    (mapv (fn [{:keys [route-data original-segments]}]
            [(str "/" (str/join "/" original-segments)) route-data])
          compiled-routes))

  (compiled-routes [_] compiled-routes)

  (options [_] {})

  (route-names [_]
    (keep (comp :name :route-data) compiled-routes))

  (match-by-path [_ path]
    (some (fn [{:keys [pattern group-keys route-data template]}]
            (when-let [matches (re-matches pattern path)]
              (let [params (zipmap group-keys (rest matches))]
                (r/->Match template route-data nil params path))))
          compiled-routes))

  (match-by-name [this name]
    (r/match-by-name this name {}))

  (match-by-name [router name path-params]
    (when-let [{:keys [group-keys route-data template] :as route}
               (first (filter #(= name (get-in % [:route-data :name])) (r/compiled-routes router)))]
      ;; Check if all required params are provided
      (let [required-params (set group-keys)
            provided-params (set (keys path-params))]
        (if (every? #(contains? provided-params %) required-params)
          ;; All required params provided, return a Match
          (let [path (generate-path route path-params)]
            (r/->Match template route-data nil path-params path))
          ;; Some required params missing, return a PartialMatch
          (let [missing (set/difference required-params provided-params)]
            (r/->PartialMatch template route-data nil path-params missing)))))))

(defn create-regex-router
  "Create a RegexRouter from a vector of routes.
   Each route should be a vector [path route-data]."
  [routes]
  (->RegexRouter (mapv compile-regex-route routes)))
