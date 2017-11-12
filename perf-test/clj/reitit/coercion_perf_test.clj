(ns reitit.coercion-perf-test
  (:require [clojure.test :refer [deftest testing is]]
            [criterium.core :as cc]
            [reitit.perf-utils :refer :all]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]

            [reitit.core :as reitit]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.coercion.spec :as spec]
            [reitit.ring.coercion.protocol :as protocol]
            [spec-tools.data-spec :as ds]
            [reitit.core :as r]))

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
          coercers (#'coercion/request-coercers spec/coercion {:body spec})
          params {:x "1", :y "2"}
          request {:body-params {:x "1", :y "2"}}]

      ;; 4600ns
      (bench!
        "coerce-parameters"
        (#'coercion/coerce-parameters coercers request))

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
  protocol/Coercion
  (get-name [_] :no-op)
  (compile [_ model _] model)
  (get-apidocs [_ _ {:keys [parameters responses] :as info}])
  (make-open [_ spec] spec)
  (encode-error [_ error] error)
  (request-coercer [_ type spec] (fn [value format] value))
  (response-coercer [this spec] (protocol/request-coercer this :response spec)))

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
                  {:meta {:middleware [coercion/wrap-coerce-parameters]
                          :coercion coercion}}))
          app2 (ring/ring-handler
                 (ring/router
                   routes
                   {:meta {:middleware [coercion/gen-wrap-coerce-parameters]
                           :coercion coercion}}))
          app3 (ring/ring-handler
                 (ring/router
                   routes
                   {:meta {:middleware [coercion/wrap-coerce-parameters
                                        coercion/wrap-coerce-response]
                           :coercion coercion}}))
          app4 (ring/ring-handler
                 (ring/router
                   routes
                   {:meta {:middleware [coercion/gen-wrap-coerce-parameters
                                        coercion/gen-wrap-coerce-response]
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
      (bench! "gen-wrap-coerce-parameters" (app2 req))

      ;;   300ns
      ;;  1740ns
      ;;  9400ns
      (bench! "wrap-coerce-parameters & responses" (app3 req))

      ;;  175ns (-42%)
      ;;  384ns (-78%)
      ;; 6100ns (-35%)
      (bench! "gen-wrap-coerce-parameters & responses" (app4 req)))))

(comment
  (do
    (require '[reitit.core :as ring])
    (require '[reitit.coercion :as coercion])
    (require '[reitit.coercion.spec :as spec])

    (def app
      (ring/ring-handler
        (ring/router
          ["/api"
           ["/ping" {:parameters {:body {:x int?, :y int?}}
                     :responses {200 {:schema {:total pos-int?}}}
                     :get {:handler (fn [{{{:keys [x y]} :body} :parameters}]
                                      {:status 200
                                       :body {:total (+ x y)}})}}]]
          {:meta {:middleware [coercion/gen-wrap-coerce-parameters
                               coercion/gen-wrap-coerce-response]
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
