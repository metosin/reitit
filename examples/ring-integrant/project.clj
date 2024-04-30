(defproject ring-integrant-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Integrant"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [ring/ring-jetty-adapter "1.12.1"]
                 [metosin/reitit "0.7.0-alpha8"]
                 [integrant "0.8.1"]]
  :main example.server
  :repl-options {:init-ns user}
  :profiles {:dev {:dependencies [[integrant/repl "0.3.3"]]
                   :source-paths ["dev"]}})
