(defproject http "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Swagger"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [org.clojure/core.async "1.6.681"]
                 [funcool/promesa "11.0.678"]
                 [manifold "0.4.2"]
                 [ring/ring-jetty-adapter "1.12.1"]
                 [metosin/reitit "0.9.1"]]
  :repl-options {:init-ns example.server})
