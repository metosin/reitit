(ns reitit.ring.middleware.alpha.muuntaja
  (:require [muuntaja.core :as m]
            [muuntaja.middleware]
            [clojure.spec.alpha :as s]))

(s/def ::muuntaja (partial instance? m/Muuntaja))

(defn create-format-middleware
  ([]
   (create-format-middleware m/default-options))
  ([options]
   {:name ::formats
    :spec (s/keys :opt-un [::muuntaja])
    :compile (fn [{:keys [muuntaja]} _]
               (let [options (or muuntaja options)]
                 (if options
                   (let [m (m/create options)]
                     {:data {:swagger {:produces (m/encodes m)
                                       :consumes (m/decodes m)}}
                      :wrap (fn [handler]
                              (muuntaja.middleware/wrap-format handler m))}))))}))
