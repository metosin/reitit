(ns reitit.ring.middleware.muuntaja
  (:require [muuntaja.core :as m]
            [muuntaja.middleware]
            [clojure.spec.alpha :as s]))

(s/def ::muuntaja (partial instance? m/Muuntaja))
(s/def ::spec (s/keys :opt-un [::muuntaja]))

(def format-middleware
  {:name ::formats
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:produces (m/encodes muuntaja)
                                  :consumes (m/decodes muuntaja)}}
                 :wrap #(muuntaja.middleware/wrap-format % muuntaja)}))})

(def format-negotiate-middleware
  {:name ::formats
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:wrap #(muuntaja.middleware/wrap-format-negotiate % muuntaja)}))})

(def format-request-middleware
  {:name ::formats
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:consumes (m/decodes muuntaja)}}
                 :wrap #(muuntaja.middleware/wrap-format-request % muuntaja)}))})

(def format-response-middleware
  {:name ::formats
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:produces (m/encodes muuntaja)}}
                 :wrap #(muuntaja.middleware/wrap-format-response % muuntaja)}))})
