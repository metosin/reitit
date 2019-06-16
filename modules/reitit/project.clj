(defproject metosin/reitit "0.3.9"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"
        :dir "../.."}
  :plugins [[lein-parent "0.3.2"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/reitit-core]
                 [metosin/reitit-dev]
                 [metosin/reitit-spec]
                 [metosin/reitit-schema]
                 [metosin/reitit-ring]
                 [metosin/reitit-middleware]
                 [metosin/reitit-http]
                 [metosin/reitit-interceptors]
                 [metosin/reitit-swagger]
                 [metosin/reitit-swagger-ui]
                 [metosin/reitit-frontend]
                 [metosin/reitit-sieppari]])
