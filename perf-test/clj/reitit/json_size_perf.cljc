(ns reitit.json-size-perf
  (:require [criterium.core :as cc]
            [reitit.perf-utils :refer [title]]
            [reitit.ring :as ring]
            [muuntaja.middleware :as mm]
            [reitit.coercion.spec]
            [reitit.ring.coercion]
            [jsonista.core :as j]))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro113
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(defn test! []
  (let [json-request (fn [data]
                       {:uri "/echo"
                        :request-method :post
                        :headers {"content-type" "application/json"
                                  "accept" "application/json"}
                        :body (j/write-value-as-string data)})
        request-stream (fn [request]
                         (let [b (.getBytes ^String (:body request))]
                           (fn []
                             (assoc request :body (java.io.ByteArrayInputStream. b)))))
        app (ring/ring-handler
              (ring/router
                ["/echo"
                 {:post {:parameters {:body any?}
                         :coercion reitit.coercion.spec/coercion
                         :handler (fn [request]
                                    (let [body (-> request :parameters :body)]
                                      {:status 200
                                       :body body}))}}]
                {:data {:middleware [mm/wrap-format
                                     reitit.ring.coercion/coerce-request-middleware]}}))]
    (doseq [file ["dev-resources/json/json10b.json"
                  "dev-resources/json/json100b.json"
                  "dev-resources/json/json1k.json"
                  "dev-resources/json/json10k.json"
                  "dev-resources/json/json100k.json"]
            :let [data (j/read-value (slurp file))
                  request (json-request data)
                  request! (request-stream request)]]

      "10b"
      ;; 38µs (c-api 1.x)
      ;; 14µs (c-api 2.0.0-alpha21)
      ;;  6µs

      "100b"
      ;; 74µs (c-api 1.x)
      ;; 16µs (c-api 2.0.0-alpha21)
      ;;  8µs

      "1k"
      ;; 322µs (c-api 1.x)
      ;;  24µs (c-api 2.0.0-alpha21)
      ;;  16µs

      "10k"
      ;; 3300µs (c-api 1.x)
      ;;  120µs (c-api 2.0.0-alpha21)
      ;;  110µs

      "100k"
      ;; 10600µs (c-api 1.x)
      ;;  1100µs (c-api 2.0.0-alpha21)
      ;;  1100µs

      (title file)
      #_(println (-> (request!) app :body slurp))
      (cc/quick-bench (app (request!))))))

(comment
  (test!))
