(defproject metosin/reitit-parent "0.1.2-SNAPSHOT"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :test-paths ["test/clj" "test/cljc"]
  :deploy-repositories [["releases" :clojars]]
  :codox {:output-path "doc"
          :source-uri "https://github.com/metosin/reitit/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}

  :managed-dependencies [[metosin/reitit "0.1.2-SNAPSHOT"]
                         [metosin/reitit-core "0.1.2-SNAPSHOT"]
                         [metosin/reitit-ring "0.1.2-SNAPSHOT"]
                         [metosin/reitit-spec "0.1.2-SNAPSHOT"]
                         [metosin/reitit-schema "0.1.2-SNAPSHOT"]
                         [metosin/reitit-swagger "0.1.2-SNAPSHOT"]
                         [metosin/reitit-swagger-ui "0.1.2-SNAPSHOT"]

                         [meta-merge "1.0.0"]
                         [ring/ring-core "1.6.3"]
                         [metosin/spec-tools "0.7.0"]
                         [metosin/schema-tools "0.10.3"]
                         [metosin/ring-swagger-ui "2.2.10"]
                         [metosin/jsonista "0.2.1"]]

  :plugins [[jonase/eastwood "0.2.6"]
            [lein-doo "0.1.10"]
            [lein-cljsbuild "1.1.7"]
            [lein-cloverage "1.0.10"]
            [lein-codox "0.10.3"]
            [metosin/bat-test "0.4.0"]]

  :profiles {:dev {:jvm-opts ^:replace ["-server"]

                   ;; all module sources for development
                   :source-paths ["modules/reitit/src"
                                  "modules/reitit-core/src"
                                  "modules/reitit-ring/src"
                                  "modules/reitit-spec/src"
                                  "modules/reitit-schema/src"
                                  "modules/reitit-swagger/src"
                                  "modules/reitit-swagger-ui/src"]

                   :dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.10.238"]

                                  ;; modules dependencies
                                  [metosin/reitit]

                                  [expound "0.6.0"]
                                  [orchestra "2017.11.12-1"]

                                  [ring "1.6.3"]
                                  [ikitommi/immutant-web "3.0.0-alpha1"]
                                  [metosin/muuntaja "0.6.0-SNAPSHOT"]
                                  [metosin/ring-swagger-ui "2.2.10"]
                                  [metosin/jsonista "0.2.1"]

                                  [criterium "0.4.4"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [com.gfredericks/test.chuck "0.2.9"]]}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]
                    :test-paths ["perf-test/clj"]
                    :dependencies [[compojure "1.6.1"]
                                   [ring/ring-defaults "0.3.1"]
                                   [ikitommi/immutant-web "3.0.0-alpha1"]
                                   [io.pedestal/pedestal.route "0.5.3"]
                                   [org.clojure/core.async "0.4.474"]
                                   [yada "1.2.13"]
                                   [ring/ring-defaults "0.3.1"]
                                   [ataraxy "0.4.0"]
                                   [bidi "2.1.3"]]}
             :analyze {:jvm-opts ^:replace ["-server"
                                            "-Dclojure.compiler.direct-linking=true"
                                            "-XX:+PrintCompilation"
                                            "-XX:+UnlockDiagnosticVMOptions"
                                            "-XX:+PrintInlining"]}}
  :aliases {"all" ["with-profile" "dev,default"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-clj" ["all" "do" ["bat-test"] ["check"]]
            "test-browser" ["doo" "chrome-headless" "test"]
            "test-advanced" ["doo" "chrome-headless" "advanced-test"]
            "test-node" ["doo" "node" "node-test"]}

  :bat-test {:report [:pretty
                      {:type :junit
                       :output-to "target/results/reitit/junit.xml"}]}

  :doo {:paths {:karma "./node_modules/.bin/karma"}
        :karma {:config {"plugins" ["karma-junit-reporter"]
                         "reporters" ["progress", "junit"]
                         "junitReporter" {"outputDir" "target/results/cljs"}}}}

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
