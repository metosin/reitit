(ns example.naive
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]))

(s/def ::x int?)
(s/def ::y int?)
(s/def ::request (s/keys :req-un [::x ::y]))

(defn ->long [x]
  (try
    (Long/parseLong x)
    (catch Exception _
      x)))

(defn app [request]
  (println (:query-params request))
  (let [{:keys [x y] :as params} (-> (:query-params request)
                                     (walk/keywordize-keys)
                                     (update :x ->long)
                                     (update :y ->long))]
    (if (s/valid? ::request params)
      {:status 200
       :body {:result (+ x y)
              :source :naive}}
      {:status 400
       :body "invalid input"})))
