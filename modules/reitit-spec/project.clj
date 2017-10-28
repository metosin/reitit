(load-file "../../.deps-versions.clj")
(defproject metosin/reitit-spec reitit-version
  :description "Reitit: clojure.spec coercion"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[metosin/reitit-ring ~reitit-version]
                 [metosin/spec-tools "0.5.0"]])
