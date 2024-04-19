(defproject openapi "0.1.0-SNAPSHOT"
  :description "Reitit OpenAPI example"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [metosin/jsonista "0.2.6"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [metosin/reitit "0.7.0-alpha7"]
                 [metosin/ring-swagger-ui "5.9.0"]]
  :repl-options {:init-ns example.server}
  :profiles {:dev {:dependencies [[ring/ring-mock "0.3.2"]]}})
