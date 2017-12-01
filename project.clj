(defproject metosin/reitit-parent "0.1.0-SNAPSHOT"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :test-paths ["test/clj" "test/cljc"]
  :deploy-repositories [["releases" :clojars]]
  :codox {:output-path "doc"
          :source-uri "https://github.com/metosin/reitit/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}

  :managed-dependencies [[metosin/reitit "0.1.0-SNAPSHOT"]
                         [metosin/reitit-core "0.1.0-SNAPSHOT"]
                         [metosin/reitit-ring "0.1.0-SNAPSHOT"]
                         [metosin/reitit-spec "0.1.0-SNAPSHOT"]
                         [metosin/reitit-schema "0.1.0-SNAPSHOT"]

                         [meta-merge "1.0.0"]
                         [metosin/spec-tools "0.5.1"]
                         [metosin/schema-tools "0.10.0-SNAPSHOT"]]

  :plugins [[jonase/eastwood "0.2.5"]
            [lein-doo "0.1.8"]
            [lein-cljsbuild "1.1.7"]
            [lein-cloverage "1.0.9"]
            [lein-codox "0.10.3"]
            [metosin/boot-alt-test "0.4.0-20171019.180106-3"]]

  :profiles {:dev {:jvm-opts ^:replace ["-server"]

                   ;; all module sources for development
                   :source-paths ["modules/reitit/src"
                                  "modules/reitit-core/src"
                                  "modules/reitit-ring/src"
                                  "modules/reitit-spec/src"
                                  "modules/reitit-schema/src"]

                   :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                                  [org.clojure/clojurescript "1.9.946"]

                                  ;; modules dependencies
                                  [metosin/reitit]
                                  [metosin/schema-tools "0.10.0-SNAPSHOT"]

                                  [expound "0.3.2"]
                                  [orchestra "2017.08.13"]

                                  [metosin/muuntaja "0.4.1"]
                                  [metosin/jsonista "0.1.0-SNAPSHOT"]

                                  [criterium "0.4.4"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [com.gfredericks/test.chuck "0.2.8"]]}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]
                    :test-paths ["perf-test/clj"]
                    :dependencies [[compojure "1.6.0"]
                                   [io.pedestal/pedestal.route "0.5.3"]
                                   [org.clojure/core.async "0.3.443"]
                                   [ataraxy "0.4.0"]
                                   [bidi "2.1.2"]]}
             :analyze {:jvm-opts ^:replace ["-server"
                                            "-Dclojure.compiler.direct-linking=true"
                                            "-XX:+PrintCompilation"
                                            "-XX:+UnlockDiagnosticVMOptions"
                                            "-XX:+PrintInlining"]}}
  :aliases {"all" ["with-profile" "dev"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-clj" ["all" "do" ["alt-test"] ["check"]]
            "test-browser" ["doo" "chrome-headless" "test"]
            "test-advanced" ["doo" "chrome-headless" "advanced-test"]
            "test-node" ["doo" "node" "node-test"]}

  :alt-test {:report [:pretty
                      {:type :junit
                       :output-to "target/junit.xml"}]}

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
