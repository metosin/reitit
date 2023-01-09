(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Swagger"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [metosin/jsonista "0.2.6"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [metosin/reitit "0.5.18"]]
  :repl-options {:init-ns example.server}
  :profiles {:dev {:dependencies [[ring/ring-mock "0.3.2"]]}})
