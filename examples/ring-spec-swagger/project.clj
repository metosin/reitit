(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Swagger"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [ring/ring-jetty-adapter "1.12.1"]
                 [metosin/reitit "0.8.0"]
                 [metosin/ring-swagger-ui "5.9.0"]]
  :repl-options {:init-ns example.server}
  :profiles {:dev {:dependencies [[ring/ring-mock "0.4.0"]]}})
