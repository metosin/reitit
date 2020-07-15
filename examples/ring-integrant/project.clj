(defproject ring-integrant-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Integrant"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [metosin/reitit "0.5.5"]
                 [integrant "0.7.0"]]
  :main example.server
  :repl-options {:init-ns user}
  :profiles {:dev {:dependencies [[integrant/repl "0.3.1"]]
                   :source-paths ["dev"]}})
