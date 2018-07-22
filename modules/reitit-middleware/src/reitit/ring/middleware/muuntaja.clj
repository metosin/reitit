(ns reitit.ring.middleware.muuntaja
  (:require [muuntaja.core :as m]
            [muuntaja.middleware]
            [clojure.spec.alpha :as s]))

(s/def ::muuntaja (partial instance? m/Muuntaja))

(def format-middleware
  {:name ::formats
   :spec (s/keys :opt-un [::muuntaja])
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:produces (m/encodes muuntaja)
                                  :consumes (m/decodes muuntaja)}}
                 :wrap (fn [handler]
                         (muuntaja.middleware/wrap-format handler muuntaja))}))})
