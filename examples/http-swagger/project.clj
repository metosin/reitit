(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit Http App with Swagger"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ring "1.6.3"]
                 [metosin/reitit "0.2.1"]]
  :repl-options {:init-ns example.server})
