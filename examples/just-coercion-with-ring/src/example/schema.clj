(ns example.schema
  (:require [reitit.coercion.schema :as schema-coercion]
            [example.middleware :as middleware]))

(defn handler [{{{:keys [x y]} :query} :parameters}]
  {:status 200
   :body {:result (+ x y)
          :source :schema}})

(def app
  (-> #'handler
      (middleware/wrap-coercion
        {:parameters {:query {:x Long, :y Long}}
         :coercion schema-coercion/coercion})))
