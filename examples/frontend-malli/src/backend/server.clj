(ns backend.server
  (:require [ring.util.response :as resp]
            [ring.middleware.content-type :as content-type]))

(def handler
  (-> (fn [request]
        (or (resp/resource-response (:uri request) {:root "public"})
            (-> (resp/resource-response "index.html" {:root "public"})
                (resp/content-type "text/html"))))
      content-type/wrap-content-type))
