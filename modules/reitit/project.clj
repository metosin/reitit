(defproject metosin/reitit "0.1.2-SNAPSHOT"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-parent "0.3.2"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/reitit-core]
                 [metosin/reitit-ring]
                 [metosin/reitit-spec]
                 [metosin/reitit-schema]
                 [metosin/reitit-swagger]
                 [metosin/reitit-swagger-ui]])
