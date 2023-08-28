(defproject fi.metosin/reitit-parent "0.7.0-alpha5"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :test-paths ["test/clj" "test/cljc"]
  :deploy-repositories {"clojars" {:url "https://repo.clojars.org"
                                   :sign-releases false
                                   :username :env/clojars_username
                                   :password :env/clojars_password}}
  :codox {:output-path "doc"
          :source-uri "https://github.com/metosin/reitit/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"}
  ;; TODO: need to verify that the code actually worked with Java1.8, see #242
  :javac-options ["-Xlint:unchecked" "-target" "1.8" "-source" "1.8"]
  :managed-dependencies [[fi.metosin/reitit "0.7.0-alpha5"]
                         [fi.metosin/reitit-core "0.7.0-alpha5"]
                         [fi.metosin/reitit-dev "0.7.0-alpha5"]
                         [fi.metosin/reitit-spec "0.7.0-alpha5"]
                         [fi.metosin/reitit-malli "0.7.0-alpha5"]
                         [fi.metosin/reitit-schema "0.7.0-alpha5"]
                         [fi.metosin/reitit-ring "0.7.0-alpha5"]
                         [fi.metosin/reitit-middleware "0.7.0-alpha5"]
                         [fi.metosin/reitit-http "0.7.0-alpha5"]
                         [fi.metosin/reitit-interceptors "0.7.0-alpha5"]
                         [fi.metosin/reitit-swagger "0.7.0-alpha5"]
                         [fi.metosin/reitit-openapi "0.7.0-alpha5"]
                         [fi.metosin/reitit-swagger-ui "0.7.0-alpha5"]
                         [fi.metosin/reitit-frontend "0.7.0-alpha5"]
                         [fi.metosin/reitit-sieppari "0.7.0-alpha5"]
                         [fi.metosin/reitit-pedestal "0.7.0-alpha5"]
                         [metosin/ring-swagger-ui "4.18.1"]
                         [metosin/spec-tools "0.10.5"]
                         [metosin/schema-tools "0.13.1"]
                         [metosin/muuntaja "0.6.8"]
                         [metosin/jsonista "0.3.7"]
                         [metosin/sieppari "0.0.0-alpha13"]
                         [metosin/malli "0.12.0"]

                         ;; https://clojureverse.org/t/depending-on-the-right-versions-of-jackson-libraries/5111
                         [com.fasterxml.jackson.core/jackson-core "2.15.1"]
                         [com.fasterxml.jackson.core/jackson-databind "2.15.1"]

                         [meta-merge "1.0.0"]
                         [fipp "0.6.26" :exclusions [org.clojure/core.rrb-vector]]
                         ;; Deep-diff uses this version, override olders versiom from fipp.
                         [org.clojure/core.rrb-vector "0.0.14"]
                         [expound "0.9.0"]
                         [lambdaisland/deep-diff "0.0-47"]
                         [com.bhauman/spell-spec "0.1.2"]
                         [mvxcvi/arrangement "2.1.0"]
                         [ring/ring-core "1.10.0"]

                         [io.pedestal/pedestal.service "0.5.10"]]

  :plugins [[jonase/eastwood "1.4.0"]
            ;[lein-virgil "0.1.7"]
            [lein-ancient "1.0.0-RC3"]
            [lein-doo "0.1.11"]
            [lein-cljsbuild "1.1.8"]
            [lein-cloverage "1.2.4"]
            [lein-codox "0.10.8"]
            [metosin/bat-test "0.4.4"]]

  :profiles {:dev {:jvm-opts ^:replace ["-server"]

                   ;; all module sources for development
                   :source-paths ["modules/reitit/src"
                                  "modules/reitit-core/src"
                                  "modules/reitit-dev/src"
                                  "modules/reitit-ring/src"
                                  "modules/reitit-http/src"
                                  "modules/reitit-middleware/src"
                                  "modules/reitit-openapi/src"
                                  "modules/reitit-interceptors/src"
                                  "modules/reitit-malli/src"
                                  "modules/reitit-spec/src"
                                  "modules/reitit-schema/src"
                                  "modules/reitit-swagger/src"
                                  "modules/reitit-swagger-ui/src"
                                  "modules/reitit-frontend/src"
                                  "modules/reitit-sieppari/src"
                                  "modules/reitit-pedestal/src"]

                   :java-source-paths ["modules/reitit-core/java-src"]

                   :dependencies [[org.clojure/clojure "1.11.1"]
                                  [org.clojure/clojurescript "1.10.773"]

                                  ;; modules dependencies
                                  [metosin/schema-tools "0.13.1"]
                                  [metosin/spec-tools "0.10.5"]
                                  [metosin/muuntaja "0.6.8"]
                                  [metosin/sieppari "0.0.0-alpha13"]
                                  [metosin/jsonista "0.3.7"]
                                  [metosin/malli "0.12.0"]
                                  [lambdaisland/deep-diff "0.0-47"]
                                  [meta-merge "1.0.0"]
                                  [com.bhauman/spell-spec "0.1.2"]
                                  [expound "0.9.0"]
                                  [fipp "0.6.26"]

                                  [orchestra "2021.01.01-1"]

                                  [ring "1.10.0"]
                                  [ikitommi/immutant-web "3.0.0-alpha1"]
                                  [metosin/ring-http-response "0.9.3"]
                                  [metosin/ring-swagger-ui "4.18.1"]
                                  [org.clojure/tools.analyzer "1.1.1"]

                                  [criterium "0.4.6"]
                                  [org.clojure/test.check "1.1.1"]
                                  [org.clojure/tools.namespace "1.4.4"]
                                  [com.gfredericks/test.chuck "0.2.14"]
                                  [nubank/matcher-combinators "3.8.5"]

                                  [io.pedestal/pedestal.service "0.5.10"]

                                  [org.clojure/core.async "1.6.673"]
                                  [manifold "0.4.1"]
                                  [funcool/promesa "11.0.664"]

                                  [com.clojure-goes-fast/clj-async-profiler "1.0.3"]
                                  [ring-cors "0.1.13"]

                                  [com.bhauman/rebel-readline "0.1.4"]]}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]
                    :test-paths ["perf-test/clj"]
                    :dependencies [[compojure "1.7.0"]
                                   [ring/ring-defaults "0.3.4"]
                                   [ikitommi/immutant-web "3.0.0-alpha1"]
                                   [io.pedestal/pedestal.service "0.5.10"]
                                   [io.pedestal/pedestal.jetty "0.5.10"]
                                   [calfpath "0.8.1"]
                                   [org.clojure/core.async "1.6.673"]
                                   [manifold "0.4.1"]
                                   [funcool/promesa "11.0.664"]
                                   [metosin/sieppari]
                                   [yada "1.2.16"]
                                   [aleph "0.6.1"]
                                   [ring/ring-defaults "0.3.4"]
                                   [ataraxy "0.4.3"]
                                   [bidi "2.1.6"]
                                   [janus "1.3.2"]]}
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
