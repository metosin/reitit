(defproject pedestal-malli-swagger-example "0.1.0-SNAPSHOT"
  :description "Reitit-http with pedestal"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.pedestal/pedestal.service "0.5.5"]
                 [io.pedestal/pedestal.jetty "0.5.5"]
                 [metosin/reitit-malli "0.7.0-alpha6"]
                 [metosin/reitit-pedestal "0.7.0-alpha6"]
                 [metosin/reitit "0.7.0-alpha6"]]
  :repl-options {:init-ns server})
