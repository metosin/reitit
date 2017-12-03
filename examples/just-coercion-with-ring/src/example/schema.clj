(ns example.schema
  (:require [reitit.ring.coercion :as coercion]
            [reitit.ring.coercion.schema :as schema-coercion]
            [example.server :as server]))

(defn handler [{{{:keys [x y]} :query} :parameters}]
  {:status 200
   :body {:result (+ x y)
          :source :schema}})

(def app
  (-> #'handler
      (server/wrap-coercion
        {:parameters {:query {:x Long, :y Long}}
         :coercion schema-coercion/coercion})))
