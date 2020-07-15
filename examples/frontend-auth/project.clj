(defproject frontend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring-server "0.5.0"]
                 [reagent "0.8.1"]
                 [ring "1.7.1"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojurescript "1.10.439"]
                 [metosin/reitit "0.5.5"]
                 [metosin/reitit-schema "0.5.5"]
                 [metosin/reitit-frontend "0.5.5"]
                 ;; Just for pretty printting the match
                 [fipp "0.6.14"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.18"]]

  :source-paths []
  :resource-paths ["resources" "target/cljsbuild"]

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.10"]]}}

  :cljsbuild
  {:builds
   [{:id "app"
     :figwheel true
     :source-paths ["src"]
     :watch-paths ["src" "checkouts/reitit-frontend/src"]
     :compiler {:main "frontend.core"
                :asset-path "/js/out"
                :output-to "target/cljsbuild/public/js/app.js"
                :output-dir "target/cljsbuild/public/js/out"
                :source-map true
                :optimizations :none
                :pretty-print true
                :preloads [devtools.preload]}}
    {:id "min"
     :source-paths ["src"]
     :compiler {:output-to "target/cljsbuild/public/js/app.js"
                :output-dir "target/cljsbuild/public/js"
                :source-map "target/cljsbuild/public/js/app.js.map"
                :optimizations :advanced
                :pretty-print false}}]}

  :figwheel {:http-server-root "public"
             :server-port 3449
             :nrepl-port 7002
             ;; Server index.html for all routes for HTML5 routing
             :ring-handler backend.server/handler})
