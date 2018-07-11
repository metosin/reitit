(defproject frontend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ring-server "0.5.0"]
                 [reagent "0.8.1"]
                 [ring "1.6.3"]
                 [compojure "1.6.1"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojurescript "1.10.339" :scope "provided"]
                 [metosin/reitit "0.1.4-SNAPSHOT"]
                 [metosin/reitit-schema "0.1.4-SNAPSHOT"]
                 [metosin/reitit-frontend "0.1.4-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.1.7"]]

  :source-paths []
  :resource-paths ["resources" "target/cljsbuild"]

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

  :figwheel
  {:http-server-root "public"
   :server-port 3449
   :nrepl-port 7002}

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.10"]]
                   :plugins [[lein-figwheel "0.5.16"]]}})
