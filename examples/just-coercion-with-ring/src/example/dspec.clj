(ns example.dspec
  (:require [reitit.ring.coercion-middleware :as coercion]
            [reitit.ring.coercion.spec :as spec-coercion]
            [example.server :as server]))

(defn handler [{{{:keys [x y]} :query} :parameters}]
  {:status 200
   :body {:result (+ x y)
          :source :data-spec}})

(def app
  (-> #'handler
      (server/wrap-coercion
        {:parameters {:query {:x int?, :y int?}}
         :coercion spec-coercion/coercion})))
