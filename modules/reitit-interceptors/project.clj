(defproject metosin/reitit-interceptors "0.9.1"
  :description "Reitit, common interceptors bundled"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"
        :dir "../.."}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/reitit-ring]
                 [lambdaisland/deep-diff]
                 [metosin/muuntaja]
                 [metosin/spec-tools]])
