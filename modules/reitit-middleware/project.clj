(defproject metosin/reitit-middleware "0.5.5"
  :description "Reitit, common middleware bundled"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"
        :scm "../.."}
  :plugins [[lein-parent "0.3.2"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/reitit-ring]
                 [lambdaisland/deep-diff]
                 [metosin/muuntaja]
                 [metosin/spec-tools]])
