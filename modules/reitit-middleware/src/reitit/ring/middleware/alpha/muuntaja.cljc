(ns reitit.ring.middleware.alpha.muuntaja
  (:require [muuntaja.core :as m]
            [muuntaja.middleware]))

(defn create-format-middleware
  ([]
   (create-format-middleware m/default-options))
  ([options]
   {:name ::formats
    :compile (fn [{:keys [muuntaja]} _]
               (let [options (or muuntaja options)]
                 (if options
                   (let [m (m/create options)]
                     {:data {:swagger {:produces (m/encodes m)
                                       :consumes (m/encodes m)}}
                      :wrap (fn [handler]
                              (muuntaja.middleware/wrap-format handler m))}))))}))
