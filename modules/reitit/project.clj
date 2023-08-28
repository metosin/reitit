(defproject fi.metosin/reitit "0.7.0-alpha5"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"
        :dir "../.."}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[fi.metosin/reitit-core]
                 [fi.metosin/reitit-dev]
                 [fi.metosin/reitit-spec]
                 [fi.metosin/reitit-malli]
                 [fi.metosin/reitit-schema]
                 [fi.metosin/reitit-ring]
                 [fi.metosin/reitit-middleware]
                 [fi.metosin/reitit-http]
                 [fi.metosin/reitit-interceptors]
                 [fi.metosin/reitit-swagger]
                 [fi.metosin/reitit-openapi]
                 [fi.metosin/reitit-swagger-ui]
                 [fi.metosin/reitit-frontend]
                 [fi.metosin/reitit-sieppari]

                 ;; https://clojureverse.org/t/depending-on-the-right-versions-of-jackson-libraries/5111
                 [com.fasterxml.jackson.core/jackson-core]
                 [com.fasterxml.jackson.core/jackson-databind]])
