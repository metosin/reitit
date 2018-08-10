(ns reitit.ring.middleware.muuntaja
  (:require [muuntaja.core :as m]
            [muuntaja.middleware]
            [clojure.spec.alpha :as s]))

(s/def ::muuntaja m/muuntaja?)
(s/def ::spec (s/keys :opt-un [::muuntaja]))

(defn- displace [x] (with-meta x {:displace true}))

(def format-middleware
  {:name ::format
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:produces (displace (m/encodes muuntaja))
                                  :consumes (displace (m/decodes muuntaja))}}
                 :wrap #(muuntaja.middleware/wrap-format % muuntaja)}))})

(def format-negotiate-middleware
  {:name ::format-negotiate
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:wrap #(muuntaja.middleware/wrap-format-negotiate % muuntaja)}))})

(def format-request-middleware
  {:name ::format-request
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:consumes (displace (m/decodes muuntaja))}}
                 :wrap #(muuntaja.middleware/wrap-format-request % muuntaja)}))})

(def format-response-middleware
  {:name ::format-response
   :spec ::spec
   :compile (fn [{:keys [muuntaja]} _]
              (if muuntaja
                {:data {:swagger {:produces (displace (m/encodes muuntaja))}}
                 :wrap #(muuntaja.middleware/wrap-format-response % muuntaja)}))})
