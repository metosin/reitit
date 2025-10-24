(defproject metosin/reitit-dev "0.9.2-rc1"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/reitit-core]
                 [com.bhauman/spell-spec]
                 [expound]
                 [fipp]
                 [org.clojure/core.rrb-vector]
                 [mvxcvi/arrangement]])
