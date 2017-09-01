(defproject metosin/reitit "0.1.0-SNAPSHOT"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :test-paths ["test/clj" "test/cljc"]
  :deploy-repositories [["releases" :clojars]]
  :codox {:output-path "doc"
          :source-uri "https://github.com/metosin/reitit/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}

  :dependencies [[meta-merge "1.0.0"]]

  :profiles {:dev {:plugins [[jonase/eastwood "0.2.3"]
                             [lein-tach "0.3.0"]
                             [lein-doo "0.1.7"]
                             [lein-cljsbuild "1.1.6"]
                             [lein-cloverage "1.0.9"]
                             [lein-codox "0.10.3"]]
                   :jvm-opts ^:replace ["-server"]
                   :dependencies [[org.clojure/clojure "1.9.0-alpha19"]
                                  [org.clojure/clojurescript "1.9.660"]

                                  [metosin/spec-tools "0.3.3"]
                                  [org.clojure/spec.alpha "0.1.123"]

                                  [expound "0.2.1"]
                                  [orchestra "2017.08.13"]

                                  [criterium "0.4.4"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [com.gfredericks/test.chuck "0.2.7"]]}
             :perf {:jvm-opts ^:replace ["-server"]
                    :test-paths ["perf-test/clj"]
                    :dependencies [[metosin/compojure-api "2.0.0-alpha7"]
                                   [io.pedestal/pedestal.route "0.5.2"]
                                   [org.clojure/core.async "0.3.443"]
                                   [ataraxy "0.4.0"]
                                   [bidi "2.0.9"]]}}
  :aliases {"all" ["with-profile" "dev"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-clj" ["all" "do" ["test"] ["check"]]
            "test-phantom" ["doo" "phantom" "test"]
            "test-advanced" ["doo" "phantom" "advanced-test"]
            "test-node" ["doo" "node" "node-test"]}
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/out/test.js"
                                   :output-dir "target/out"
                                   :main reitit.doo-runner
                                   :optimizations :none}}
                       {:id "advanced-test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/advanced_out/test.js"
                                   :output-dir "target/advanced_out"
                                   :main reitit.doo-runner
                                   :optimizations :advanced}}
                       ;; Node.js requires :target :nodejs, hence the separate
                       ;; build configuration.
                       {:id "node-test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/node_out/test.js"
                                   :output-dir "target/node_out"
                                   :main reitit.doo-runner
                                   :optimizations :none
                                   :target :nodejs}}]})
