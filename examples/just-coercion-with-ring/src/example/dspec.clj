(ns example.dspec
  (:require [reitit.coercion.spec :as spec-coercion]
            [example.middleware :as middleware]))

(defn handler [{{{:keys [x y]} :query} :parameters}]
  {:status 200
   :body {:result (+ x y)
          :source :data-spec}})

(def app
  (-> #'handler
      (middleware/wrap-coercion
        {:parameters {:query {:x int?, :y int?}}
         :coercion spec-coercion/coercion})))
