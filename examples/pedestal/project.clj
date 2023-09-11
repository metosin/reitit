(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit-http with pedestal"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.pedestal/pedestal.service "0.5.5"]
                 [io.pedestal/pedestal.jetty "0.5.5"]
                 [fi.metosin/reitit-pedestal "0.7.0-alpha6"]
                 [fi.metosin/reitit "0.7.0-alpha6"]]
  :repl-options {:init-ns example.server})
