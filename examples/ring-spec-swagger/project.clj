(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Swagger"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [fi.metosin/reitit "0.7.0-alpha6"]
                 [metosin/ring-swagger-ui "5.0.0-alpha.0"]]
  :repl-options {:init-ns example.server}
  :profiles {:dev {:dependencies [[ring/ring-mock "0.3.2"]]}})
