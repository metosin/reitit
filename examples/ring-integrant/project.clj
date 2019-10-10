(defproject ring-integrant-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Swagger"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [metosin/reitit "0.3.10"]
                 [integrant "0.7.0"]]
  :repl-options {:init-ns example.server})
