(defproject metosin/reitit-core "0.7.0-alpha7"
  :description "Snappy data-driven router for Clojure(Script)"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"
        :dir "../.."}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :java-source-paths ["java-src"]
  :javac-options ["-Xlint:unchecked" "-target" "1.8" "-source" "1.8"]
  :dependencies [[meta-merge]])
