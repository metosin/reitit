(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [metosin/reitit "0.7.0-alpha2"]]
  :repl-options {:init-ns example.server})
