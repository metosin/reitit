(defproject ring-malli-lite-swagger "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Swagger"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [metosin/jsonista "0.3.8"]
                 [ring/ring-jetty-adapter "1.12.1"]
                 [metosin/reitit "0.9.0"]]
  :repl-options {:init-ns example.server}
  :profiles {:dev {:dependencies [[ring/ring-mock "0.4.0"]]}})
