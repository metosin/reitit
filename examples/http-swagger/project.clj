(defproject http-swagger "0.1.0-SNAPSHOT"
  :description "Reitit Http App with Swagger"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [ring/ring-jetty-adapter "1.12.1"]
                 [aleph "0.7.1"]
                 [metosin/reitit "0.9.1"]
                 [metosin/ring-swagger-ui "5.9.0"]]
  :repl-options {:init-ns example.server})
