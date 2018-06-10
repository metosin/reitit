(ns example.spec
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [reitit.coercion.spec :as spec-coercion]
            [example.middleware :as middleware]))

;; wrap into Spec Records to enable runtime conforming
(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::request (s/keys :req-un [::x ::y]))

;; read coerced parameters under :parameters
(defn handler [{{{:keys [x y]} :query} :parameters}]
  {:status 200
   :body {:result (+ x y)
          :source :spec}})

(def app
  (-> #'handler
      (middleware/wrap-coercion
        {:parameters {:query ::request}
         :coercion spec-coercion/coercion})))
