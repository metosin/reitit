(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Swagger"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [ring "1.6.3"]
                 [metosin/reitit "0.2.0-SNAPSHOT"]]
  :repl-options {:init-ns example.server})
