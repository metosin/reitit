(defproject pedestal-malli-swagger-example "0.1.0-SNAPSHOT"
  :description "Reitit-http with pedestal"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.pedestal/pedestal.service "0.5.5"]
                 [io.pedestal/pedestal.jetty "0.5.5"]
                 [metosin/reitit-malli "0.5.18"]
                 [metosin/reitit-pedestal "0.5.18"]
                 [metosin/reitit "0.5.18"]]
  :repl-options {:init-ns server})
