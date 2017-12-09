(ns reitit.coercion-perf-test
  (:require [clojure.test :refer [deftest testing is]]
            [criterium.core :as cc]
            [reitit.perf-utils :refer :all]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.data-spec :as ds]
            [muuntaja.middleware :as mm]
            [muuntaja.core :as m]
            [muuntaja.format.jsonista :as jsonista-format]
            [jsonista.core :as j]
            [reitit.coercion-middleware :as coercion-middleware]
            [reitit.coercion.spec :as spec]
            [reitit.coercion.schema :as schema]
            [reitit.coercion :as coercion]
            [reitit.ring :as ring]
            [reitit.core :as r])
  (:import (java.io ByteArrayInputStream)))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro11,3
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(comment
  (do
    (s/def ::x (s/and (s/conformer #(if (string? %) (Long/parseLong %) %) identity) int?))
    (s/def ::y (s/and (s/conformer #(if (string? %) (Long/parseLong %) %) identity) int?))
    (s/def ::k (s/keys :req-un [::x ::y]))

    (let [spec (spec/into-spec {:x int?, :y int?} ::jeah)
          coercers (#'coercion-middleware/request-coercers spec/coercion {:body spec})
          params {:x "1", :y "2"}
          request {:body-params {:x "1", :y "2"}}]

      ;; 4600ns
      (bench!
        "coerce-parameters"
        (#'coercion-middleware/coerce-parameters coercers request))

      ;; 2700ns
      (bench!
        "st/conform"
        (st/conform
          spec
          params
          spec/string-conforming))

      ;; 4100ns
      (bench!
        "st/conform + s/unform"
        (s/unform
          spec
          (st/conform
            spec
            params
            spec/string-conforming)))

      ;; 610ns
      (bench!
        "s/conform"
        (s/conform
          ::k
          params))

      ;; 2700ns
      (bench!
        "s/conform + s/unform"
        (s/unform
          ::k
          (s/conform
            ::k
            params))))))

(defrecord NoOpCoercion []
  coercion/Coercion
  (-get-name [_] :no-op)
  (-get-apidocs [_ _ {:keys [parameters responses] :as info}])
  (-compile-model [_ model _] model)
  (-open-model [_ spec] spec)
  (-encode-error [_ error] error)
  (-request-coercer [_ type spec] (fn [value format] value))
  (-response-coercer [this spec] (protocol/request-coercer this :response spec)))

(comment
  (doseq [coercion [nil (->NoOpCoercion) spec/coercion]]
    (suite (str (if coercion (protocol/get-name coercion))))
    (let [routes ["/api"
                  ["/ping" {:parameters {:body {:x int?, :y int?}}
                            :responses {200 {:schema {:total pos-int?}}}
                            :get {:handler (fn [request]
                                             (let [{:keys [x y]} (-> request :parameters :body)]
                                               {:status 200
                                                :body {:total (+ (or x 0) (or y 0))}}))}}]]
          app (ring/ring-handler
                (ring/router
                  routes
                  {:data {:middleware [coercion-middleware/coerce-request-middleware]
                          :coercion coercion}}))
          app2 (ring/ring-handler
                 (ring/router
                   routes
                   {:data {:middleware [coercion-middleware/coerce-request-middleware]
                           :coercion coercion}}))
          app3 (ring/ring-handler
                 (ring/router
                   routes
                   {:data {:middleware [coercion-middleware/coerce-request-middleware
                                        coercion-middleware/wrap-coerce-response]
                           :coercion coercion}}))
          app4 (ring/ring-handler
                 (ring/router
                   routes
                   {:data {:middleware [coercion-middleware/coerce-request-middleware
                                        coercion-middleware/coerce-response-middleware]
                           :coercion coercion}}))
          req {:request-method :get
               :uri "/api/ping"
               :body-params {:x 1, :y 2}}]

      ;;   215ns
      ;;  1000ns
      ;;  5780ns
      (bench! "wrap-coerce-parameters" (app req))

      ;;   175ns (-19%)
      ;;   360ns (-64%)
      ;;  4080ns (-30%)
      (bench! "coerce-request-middleware" (app2 req))

      ;;   300ns
      ;;  1740ns
      ;;  9400ns
      (bench! "wrap-coerce-request & responses" (app3 req))

      ;;  175ns (-42%)
      ;;  384ns (-78%)
      ;; 6100ns (-35%)
      (bench! "coerce-request-middleware & responses" (app4 req)))))

(comment
  (do
    (def app
      (ring/ring-handler
        (ring/router
          ["/api"
           ["/ping" {:parameters {:body {:x int?, :y int?}}
                     :responses {200 {:schema {:total pos-int?}}}
                     :get {:handler (fn [{{{:keys [x y]} :body} :parameters}]
                                      {:status 200
                                       :body {:total (+ x y)}})}}]]
          {:data {:middleware [coercion-middleware/coerce-request-middleware
                               coercion-middleware/coerce-response-middleware]
                  :coercion spec/coercion}})))

    (app
      {:request-method :get
       :uri "/api/ping"
       :body-params {:x 1, :y 2}})
    ; {:status 200, :body {:total 3}}

    (let [req {:request-method :get
               :uri "/api/ping"
               :body-params {:x 1, :y 2}}]
      (cc/quick-bench (app req)))))

(defn json-perf-test []
  (let [m (m/create (jsonista-format/with-json-format m/default-options))
        app (ring/ring-handler
              (ring/router
                ["/plus" {:post {:handler (fn [{{:keys [x y]} :body-params}]
                                            {:status 200, :body {:result (+ x y)}})}}]
                {:data {:middleware [[mm/wrap-format m]]}}))
        request {:request-method :post
                 :uri "/plus"
                 :headers {"content-type" "application/json"}
                 :body (j/write-value-as-string {:x 1, :y 2})}
        call (fn [] (-> request app :body slurp))]
    (prn (-> request app :body slurp))

    ;; 7.8µs (cheshire)
    ;; 6.5µs (jsonista)
    (cc/quick-bench
      (-> request app :body slurp))))

(defn schema-json-perf-test []
  (let [m (m/create (jsonista-format/with-json-format m/default-options))
        app (ring/ring-handler
              (ring/router
                ["/plus" {:post {:responses {200 {:schema {:result Long}}}
                                 :parameters {:body {:x Long, :y Long}}
                                 :handler (fn [request]
                                            (let [body (-> request :parameters :body)]
                                              {:status 200, :body {:result (+ (:x body) (:y body))}}))}}]
                {:data {:middleware [[mm/wrap-format m]
                                     coercion-middleware/coerce-request-middleware
                                     coercion-middleware/coerce-response-middleware]
                        :coercion schema/coercion}}))
        request {:request-method :post
                 :uri "/plus"
                 :headers {"content-type" "application/json"}
                 :body (j/write-value-as-string {:x 1, :y 2})}
        call (fn [] (-> request app :body slurp))]
    (prn (-> request app :body slurp))

    ;; 11.6µs (cheshire)
    ;; 10.0µs (jsonista)
    (cc/quick-bench
      (-> request app :body slurp))))

(defn schema-perf-test []
  (let [app (ring/ring-handler
              (ring/router
                ["/plus" {:post {:responses {200 {:schema {:result Long}}}
                                 :parameters {:body {:x Long, :y Long}}
                                 :handler (fn [request]
                                            (let [body (-> request :parameters :body)]
                                              {:status 200, :body {:result (+ (:x body) (:y body))}}))}}]
                {:data {:middleware [coercion-middleware/coerce-request-middleware
                                     coercion-middleware/coerce-response-middleware]
                        :coercion schema/coercion}}))
        request {:request-method :post
                 :uri "/plus"
                 :body-params {:x 1, :y 2}}
        call (fn [] (-> request app :body))]
    (assert (= {:result 3} (call)))

    ;; 0.23µs (no coercion)
    ;; 12.8µs
    ;;  1.9µs (cached coercers)
    (cc/quick-bench
      (call))))

(defn data-spec-perf-test []
  (let [app (ring/ring-handler
              (ring/router
                ["/plus" {:post {:responses {200 {:schema {:result int?}}}
                                 :parameters {:body {:x int?, :y int?}}
                                 :handler (fn [request]
                                            (let [body (-> request :parameters :body)]
                                              {:status 200, :body {:result (+ (:x body) (:y body))}}))}}]
                {:data {:middleware [coercion-middleware/coerce-request-middleware
                                     coercion-middleware/coerce-response-middleware]
                        :coercion spec/coercion}}))
        request {:request-method :post
                 :uri "/plus"
                 :body-params {:x 1, :y 2}}
        call (fn [] (-> request app :body))]
    (assert (= {:result 3} (call)))

    ;;  6.0µs
    (cc/quick-bench
      (call))))

(s/def ::x int?)
(s/def ::y int?)
(s/def ::request (s/keys :req-un [::x ::y]))

(s/def ::result int?)
(s/def ::response (s/keys :req-un [::result]))

(defn spec-perf-test []
  (let [app (ring/ring-handler
              (ring/router
                ["/plus" {:post {:responses {200 {:schema ::response}}
                                 :parameters {:body ::request}
                                 :handler (fn [request]
                                            (let [body (-> request :parameters :body)]
                                              {:status 200, :body {:result (+ (:x body) (:y body))}}))}}]
                {:data {:middleware [coercion-middleware/coerce-request-middleware
                                     coercion-middleware/coerce-response-middleware]
                        :coercion spec/coercion}}))
        request {:request-method :post
                 :uri "/plus"
                 :body-params {:x 1, :y 2}}
        call (fn [] (-> request app :body))]
    (assert (= {:result 3} (call)))

    ;;  3.2µs
    (cc/quick-bench
      (call))))

(comment
  (json-perf-test)
  (schema-json-perf-test)
  (schema-perf-test)
  (data-spec-perf-test)
  (spec-perf-test))
