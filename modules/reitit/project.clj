(defproject metosin/reitit "0.5.5"
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
                 [metosin/reitit-malli]
                 [metosin/reitit-schema]
                 [metosin/reitit-ring]
                 [metosin/reitit-middleware]
                 [metosin/reitit-http]
                 [metosin/reitit-interceptors]
                 [metosin/reitit-swagger]
                 [metosin/reitit-swagger-ui]
                 [metosin/reitit-frontend]
                 [metosin/reitit-sieppari]

                 ;; https://clojureverse.org/t/depending-on-the-right-versions-of-jackson-libraries/5111
                 [com.fasterxml.jackson.core/jackson-core]
                 [com.fasterxml.jackson.core/jackson-databind]])
