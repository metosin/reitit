(defproject metosin/reitit-parent "0.9.2"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :test-paths ["test/clj" "test/cljc"]
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]
  :repositories [["clojars" {:url "https://repo.clojars.org"
                             :username :env/clojars_username
                             :password :env/clojars_password}]]
  :codox {:output-path "doc"
          :source-uri "https://github.com/metosin/reitit/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"}
  ;; Ring 1.13.1 drops support for Java 1.8 so lets target 11
  :javac-options ["-Xlint:unchecked" "-target" "11" "-source" "11"]
  :managed-dependencies [[metosin/reitit "0.9.2"]
                         [metosin/reitit-core "0.9.2"]
                         [metosin/reitit-dev "0.9.2"]
                         [metosin/reitit-spec "0.9.2"]
                         [metosin/reitit-malli "0.9.2"]
                         [metosin/reitit-schema "0.9.2"]
                         [metosin/reitit-ring "0.9.2"]
                         [metosin/reitit-middleware "0.9.2"]
                         [metosin/reitit-http "0.9.2"]
                         [metosin/reitit-interceptors "0.9.2"]
                         [metosin/reitit-swagger "0.9.2"]
                         [fi.metosin/reitit-openapi "0.9.2"]
                         [metosin/reitit-swagger-ui "0.9.2"]
                         [metosin/reitit-frontend "0.9.2"]
                         [metosin/reitit-sieppari "0.9.2"]
                         [metosin/reitit-pedestal "0.9.2"]
                         [metosin/ring-swagger-ui "5.20.0"]
                         [metosin/spec-tools "0.10.8"]
                         [metosin/schema-tools "0.13.1"]
                         [metosin/muuntaja "0.6.11"]
                         [metosin/jsonista "0.3.13"]
                         [metosin/sieppari "0.0.0-alpha13"]
                         [metosin/malli "0.20.0"]

                         ;; https://clojureverse.org/t/depending-on-the-right-versions-of-jackson-libraries/5111
                         [com.fasterxml.jackson.core/jackson-core "2.20.1"]
                         [com.fasterxml.jackson.core/jackson-databind "2.20.1"]

                         [meta-merge "1.0.0"]
                         [fipp "0.6.29" :exclusions [org.clojure/core.rrb-vector]]
                         ;; Deep-diff uses this version, override olders versiom from fipp.
                         [org.clojure/core.rrb-vector "0.2.1"]
                         [expound "0.9.0"]
                         [lambdaisland/deep-diff "0.0-47"]
                         [com.bhauman/spell-spec "0.1.2"]
                         [mvxcvi/arrangement "2.1.0"]
                         [ring/ring-core "1.15.3"]

                         [io.pedestal/pedestal.service "0.6.4" :upgrade false]]

  :plugins [[jonase/eastwood "1.4.3"]
            ;[lein-virgil "0.1.7"]
            [lein-ancient "1.0.0-RC3"]
            [lein-doo "0.1.11"]
            [lein-cljsbuild "1.1.8"]
            [lein-cloverage "1.2.4"]
            [lein-codox "0.10.8"]
            [metosin/bat-test "0.4.4"]]

  :profiles {:clj11 {:dependencies [[org.clojure/clojure "1.11.4"]]}
             :dev {:jvm-opts ^:replace ["-server"]

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

                   :dependencies [[org.clojure/clojure "1.12.4"]
                                  [thheller/shadow-cljs "3.3.4"]
                                  [org.clojure/clojurescript "1.12.134"]

                                  ;; modules dependencies
                                  [metosin/schema-tools "0.13.1"]
                                  [metosin/spec-tools "0.10.8"]
                                  [metosin/muuntaja "0.6.11"]
                                  [metosin/sieppari "0.0.0-alpha13"]
                                  [metosin/jsonista "0.3.13"]
                                  [metosin/malli "0.20.0"]
                                  [lambdaisland/deep-diff "0.0-47"]
                                  [meta-merge "1.0.0"]
                                  [com.bhauman/spell-spec "0.1.2"]
                                  [expound "0.9.0"]
                                  [fipp "0.6.29"]

                                  [orchestra "2021.01.01-1"]

                                  [ring "1.15.3"]
                                  [ikitommi/immutant-web "3.0.0-alpha1"]
                                  [metosin/ring-http-response "0.9.5"]
                                  [metosin/ring-swagger-ui "5.20.0"]
                                  [org.clojure/tools.analyzer "1.2.1"]

                                  [criterium "0.4.6"]
                                  [org.clojure/test.check "1.1.3"]
                                  [org.clojure/tools.namespace "1.5.1"]
                                  [com.gfredericks/test.chuck "0.2.15"]
                                  [nubank/matcher-combinators "3.9.2"]

                                  ;; TODO: adapt to breaking changes in pedestal 0.7 and 0.8
                                  [io.pedestal/pedestal.service "0.6.4" :upgrade false]

                                  [org.clojure/core.async "1.8.741"]
                                  [manifold "0.5.0"]
                                  [funcool/promesa "11.0.678"]

                                  [com.clojure-goes-fast/clj-async-profiler "1.7.0"]
                                  [ring-cors "0.1.13"]

                                  [com.bhauman/rebel-readline "0.1.5"]]}
             :shadow {:test-paths ["test/cljs"]}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]
                    :test-paths ["perf-test/clj"]
                    :dependencies [[compojure "1.7.2"]
                                   [ring/ring-defaults "0.7.0"]
                                   [ikitommi/immutant-web "3.0.0-alpha1"]
                                   [io.pedestal/pedestal.service "0.6.4" :upgrade false]
                                   [io.pedestal/pedestal.jetty "0.6.4" :upgrade false]
                                   [calfpath "0.8.1"]
                                   [org.clojure/core.async "1.8.741"]
                                   [manifold "0.5.0"]
                                   [funcool/promesa "11.0.678"]
                                   [metosin/sieppari]
                                   [yada "1.2.16"]
                                   [aleph "0.9.3"]
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
            "test-clj11" ["with-profile" "dev,default,clj11" "do" ["bat-test"] ["check"]]
            ;; NOTE: These are deprecated, kept around for ensuring shadow-cljs works
            ;; the same way.
            "test-browser" ["doo" "chrome-headless" "test"]
            "test-advanced" ["doo" "chrome-headless" "advanced-test"]
            "test-node" ["doo" "node" "node-test"]}

  :bat-test {:report [:pretty
                      {:type :junit
                       :output-to "target/results/reitit/junit.xml"}]}

  :doo {:paths {:karma "./node_modules/.bin/karma"}
        :karma {:config {"reporters" ["progress"]}}}

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
