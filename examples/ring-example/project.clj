(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ring/ring-jetty-adapter "1.7.0"]
                 [metosin/reitit "0.2.6"]]
  :repl-options {:init-ns example.server})
