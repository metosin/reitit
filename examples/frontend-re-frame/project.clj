(defproject frontend-re-frame "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [org.clojure/clojurescript "1.11.132"]
                 [metosin/reitit "0.7.2"]
                 [reagent "1.2.0"]
                 [re-frame "0.10.6"]
                 [cljsjs/react "17.0.2-0"]
                 [cljsjs/react-dom "17.0.2-0"]]

  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-figwheel "0.5.20"]
            [cider/cider-nrepl "0.47.1"]]

  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
  :min-lein-version "2.5.3"
  :source-paths ["src/clj" "src/cljs"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :figwheel
  {:css-dirs     ["resources/public/css"]
   :server-port  3449
   :nrepl-port   7002
   :ring-handler backend.server/handler}

  :profiles
  {:dev
   {:dependencies
    [[binaryage/devtools "0.9.10"]
     [cider/piggieback "0.4.0"]
     [figwheel-sidecar "0.5.18"]]

    :plugins [[lein-figwheel "0.5.18"]]}
   :prod {}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs"]
     :figwheel     true
     :compiler     {:main                 frontend-re-frame.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload]
                    :external-config      {:devtools/config {:features-to-install :all}}
                    }}

    {:id           "min"
     :source-paths ["src/cljs"]
     :compiler     {:main            frontend-re-frame.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}


    ]}
  )
