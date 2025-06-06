(defproject frontend-links "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.11.2"]
                 [ring-server "0.5.0"]
                 [reagent "1.2.0"]
                 [ring "1.12.1"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojurescript "1.10.520"]
                 [metosin/reitit "0.9.1"]
                 [metosin/reitit-spec "0.9.1"]
                 [metosin/reitit-frontend "0.9.1"]
                 [cljsjs/react "17.0.2-0"]
                 [cljsjs/react-dom "17.0.2-0"]
                 ;; Just for pretty printting the match
                 [fipp "0.6.14"]]

  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-figwheel "0.5.20"]
            [cider/cider-nrepl "0.47.1"]]

  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

  :source-paths ["src"]
  :resource-paths ["resources" "target/cljsbuild"]

  :profiles
  {:dev
   {:dependencies
    [[binaryage/devtools "0.9.10"]
     [cider/piggieback "0.4.0"]
     [figwheel-sidecar "0.5.18"]]}}

  :cljsbuild
  {:builds
   [{:id           "app"
     :figwheel     true
     :source-paths ["src"]
     :compiler     {:main          "frontend.core"
                    :asset-path    "/js/out"
                    :output-to     "target/cljsbuild/public/js/app.js"
                    :output-dir    "target/cljsbuild/public/js/out"
                    :source-map    true
                    :optimizations :none
                    :pretty-print  true
                    :preloads      [devtools.preload]
                    :aot-cache     true}}
    {:id           "min"
     :source-paths ["src"]
     :compiler     {:output-to     "target/cljsbuild/public/js/app.js"
                    :output-dir    "target/cljsbuild/public/js"
                    :source-map    "target/cljsbuild/public/js/app.js.map"
                    :optimizations :advanced
                    :pretty-print  false
                    :aot-cache     true}}]}

  :figwheel {:http-server-root "public"
             :server-port      3449
             :nrepl-port       7002
             ;; Server index.html for all routes for HTML5 routing
             :ring-handler     backend.server/handler})
