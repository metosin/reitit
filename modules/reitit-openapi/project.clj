(defproject fi.metosin/reitit-openapi "0.7.0-alpha6"
  :description "Reitit: OpenAPI-support"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"
        :dir "../.."}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/reitit-core]])
