(defproject metosin/reitit-swagger-ui "0.2.1"
  :description "Reitit: Swagger-ui support"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/reitit"}
  :plugins [[lein-parent "0.3.2"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/reitit-ring]
                 [metosin/jsonista]
                 [metosin/ring-swagger-ui]])
