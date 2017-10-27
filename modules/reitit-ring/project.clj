(load-file "../../.deps-versions.clj")
(defproject metosin/reitit-ring reitit-version
  :description "Ring routing with reitit"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[metosin/reitit-core ~reitit-version]])
