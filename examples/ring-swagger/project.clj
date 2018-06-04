(defproject ring-example "0.1.0-SNAPSHOT"
  :description "Reitit Ring App with Swagger"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ring "1.6.3"]
                 [metosin/muuntaja "0.5.0"]
                 [metosin/reitit "0.1.2-SNAPSHOT"]]
  :repl-options {:init-ns example.server})
