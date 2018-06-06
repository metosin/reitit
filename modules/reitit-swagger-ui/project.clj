(defproject metosin/reitit-swagger-ui "0.1.2-SNAPSHOT"
  :description "Reitit: Swagger-ui support"
  :url "https://github.com/metosin/reitit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-parent "0.3.2"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/reitit-ring]
                 [metosin/jsonista]
                 [metosin/ring-swagger-ui]])
