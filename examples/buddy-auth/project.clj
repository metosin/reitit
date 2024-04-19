(defproject buddy-auth "0.1.0-SNAPSHOT"
  :description "Reitit Buddy Auth App"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [ring/ring-jetty-adapter "1.12.1"]
                 [metosin/reitit "0.7.0-alpha7"]
                 [buddy "2.0.0"]]
  :repl-options {:init-ns example.server})
