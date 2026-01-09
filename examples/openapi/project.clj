(defproject openapi "0.1.0-SNAPSHOT"
  :description "Reitit OpenAPI example"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [metosin/jsonista "0.3.8"]
                 [ring/ring-jetty-adapter "1.12.1"]
                 [metosin/reitit "0.10.0"]
                 [metosin/ring-swagger-ui "5.9.0"]
                 [org.slf4j/slf4j-simple "2.0.9"]]
  :repl-options {:init-ns example.server}
  :profiles {:dev {:dependencies [[ring/ring-mock "0.4.0"]]}})
