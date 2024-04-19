(defproject frontend-auth "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.11.2"]
                 [ring-server "0.5.0"]
                 [reagent "1.2.0"]
                 [ring "1.12.1"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojurescript "1.11.132"]
                 [metosin/reitit "0.7.0-alpha7"]
                 [metosin/reitit-schema "0.7.0-alpha7"]
                 [metosin/reitit-frontend "0.7.0-alpha7"]
                 [cljsjs/react "17.0.2-0"]
                 [cljsjs/react-dom "17.0.2-0"]
                 ;; Just for pretty printting the match
                 [fipp "0.6.14"]]

  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-figwheel "0.5.20"]]

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
