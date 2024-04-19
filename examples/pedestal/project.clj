(defproject pedestal-example "0.1.0-SNAPSHOT"
  :description "Reitit-http with pedestal"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [io.pedestal/pedestal.service "0.6.3"]
                 [io.pedestal/pedestal.jetty "0.6.3"]
                 [metosin/reitit-pedestal "0.7.0-alpha7"]
                 [metosin/reitit "0.7.0-alpha7"]]
  :repl-options {:init-ns example.server})
