(defproject metosin/reitit-parent "0.2.9"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :test-paths ["test/clj" "test/cljc"]
  :deploy-repositories [["releases" :clojars]]
  :codox {:output-path "doc"
          :source-uri "https://github.com/metosin/reitit/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"}
  :managed-dependencies [[metosin/reitit "0.2.9"]
                         [metosin/reitit-core "0.2.9"]
                         [metosin/reitit-spec "0.2.9"]
                         [metosin/reitit-schema "0.2.9"]
                         [metosin/reitit-ring "0.2.9"]
                         [metosin/reitit-middleware "0.2.9"]
                         [metosin/reitit-http "0.2.9"]
                         [metosin/reitit-interceptors "0.2.9"]
                         [metosin/reitit-swagger "0.2.9"]
                         [metosin/reitit-swagger-ui "0.2.9"]
                         [metosin/reitit-frontend "0.2.9"]
                         [metosin/reitit-sieppari "0.2.9"]
                         [metosin/reitit-pedestal "0.2.9"]
                         [metosin/ring-swagger-ui "2.2.10"]
                         [metosin/spec-tools "0.8.2"]
                         [metosin/schema-tools "0.10.5"]
                         [metosin/muuntaja "0.6.3"]
                         [metosin/jsonista "0.2.2"]
                         [metosin/sieppari "0.0.0-alpha7"]

                         [meta-merge "1.0.0"]
                         [lambdaisland/deep-diff "0.0-25"]
                         [ring/ring-core "1.7.1"]

                         [io.pedestal/pedestal.service "0.5.5"]]

  :plugins [[jonase/eastwood "0.3.4"]
            [lein-doo "0.1.11"]
            [lein-cljsbuild "1.1.7"]
            [lein-cloverage "1.0.13"]
            [lein-codox "0.10.5"]
            [metosin/bat-test "0.4.2"]]

  :profiles {:dev {:jvm-opts ^:replace ["-server"]

                   ;; all module sources for development
                   :source-paths ["modules/reitit/src"
                                  "modules/reitit-core/src"
                                  "modules/reitit-ring/src"
                                  "modules/reitit-http/src"
                                  "modules/reitit-middleware/src"
                                  "modules/reitit-interceptors/src"
                                  "modules/reitit-spec/src"
                                  "modules/reitit-schema/src"
                                  "modules/reitit-swagger/src"
                                  "modules/reitit-swagger-ui/src"
                                  "modules/reitit-frontend/src"
                                  "modules/reitit-sieppari/src"
                                  "modules/reitit-pedestal/src"]

                   :dependencies [[org.clojure/clojure "1.10.0"]
                                  [org.clojure/clojurescript "1.10.439"]

                                  ;; modules dependencies
                                  [metosin/reitit "0.2.9"]

                                  [expound "0.7.2"]
                                  [orchestra "2018.12.06-2"]

                                  [ring "1.7.1"]
                                  [ikitommi/immutant-web "3.0.0-alpha1"]
                                  [metosin/ring-swagger-ui "2.2.10"]
                                  [metosin/muuntaja]
                                  [metosin/sieppari]
                                  [metosin/jsonista "0.2.2"]

                                  [criterium "0.4.4"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [com.gfredericks/test.chuck "0.2.9"]

                                  [io.pedestal/pedestal.service "0.5.5"]

                                  [org.clojure/core.async "0.4.490"]
                                  [manifold "0.1.8"]
                                  [funcool/promesa "1.9.0"]

                                  ;; https://github.com/bensu/doo/issues/180
                                  [fipp "0.6.14" :exclusions [org.clojure/core.rrb-vector]]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]
                    :test-paths ["perf-test/clj"]
                    :dependencies [[compojure "1.6.1"]
                                   [ring/ring-defaults "0.3.2"]
                                   [ikitommi/immutant-web "3.0.0-alpha1"]
                                   [io.pedestal/pedestal.service "0.5.5"]
                                   [io.pedestal/pedestal.jetty "0.5.5"]
                                   [org.clojure/core.async "0.4.490"]
                                   [manifold "0.1.8"]
                                   [funcool/promesa "1.9.0"]
                                   [metosin/sieppari]
                                   [yada "1.2.16"]
                                   [aleph "0.4.6"]
                                   [ring/ring-defaults "0.3.2"]
                                   [ataraxy "0.4.2"]
                                   [bidi "2.1.5"]]}
             :analyze {:jvm-opts ^:replace ["-server"
                                            "-Dclojure.compiler.direct-linking=true"
                                            "-XX:+PrintCompilation"
                                            "-XX:+UnlockDiagnosticVMOptions"
                                            "-XX:+PrintInlining"]}}
  :aliases {"all" ["with-profile" "dev,default:dev,default,1.9"]
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
