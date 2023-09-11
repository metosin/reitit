(defproject openapi "0.1.0-SNAPSHOT"
  :description "Reitit OpenAPI example"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [metosin/jsonista "0.2.6"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [fi.metosin/reitit "0.7.0-alpha5"]
                 [metosin/ring-swagger-ui "5.0.0-alpha.0"]]
  :repl-options {:init-ns example.server}
  :profiles {:dev {:dependencies [[ring/ring-mock "0.3.2"]]}})
