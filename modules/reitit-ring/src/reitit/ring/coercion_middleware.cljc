(ns reitit.ring.coercion-middleware
  (:require [reitit.coercion :as coercion]
            [reitit.impl :as impl]))

(defn handle-coercion-exception [e respond raise]
  (let [data (ex-data e)]
    (if-let [status (condp = (:type data)
                      ::coercion/request-coercion 400
                      ::coercion/response-coercion 500
                      nil)]
      (respond
        {:status status
         :body (coercion/encode-error data)})
      (raise e))))

;;
;; middleware
;;

(def coerce-request-middleware
  "Middleware for pluggable request coercion.
  Expects a :coercion of type `reitit.coercion/Coercion`
  and :parameters from route data, otherwise does not mount."
  {:name ::coerce-parameters
   :compile (fn [{:keys [coercion parameters]} opts]
              (if (and coercion parameters)
                (let [coercers (coercion/request-coercers coercion parameters opts)]
                  (fn [handler]
                    (fn
                      ([request]
                       (let [coerced (coercion/coerce-request coercers request)]
                         (handler (impl/fast-assoc request :parameters coerced))))
                      ([request respond raise]
                       (let [coerced (coercion/coerce-request coercers request)]
                         (handler (impl/fast-assoc request :parameters coerced) respond raise))))))))})

(def coerce-response-middleware
  "Middleware for pluggable response coercion.
  Expects a :coercion of type `reitit.coercion/Coercion`
  and :responses from route data, otherwise does not mount."
  {:name ::coerce-response
   :compile (fn [{:keys [coercion responses]} opts]
              (if (and coercion responses)
                (let [coercers (coercion/response-coercers coercion responses opts)]
                  (fn [handler]
                    (fn
                      ([request]
                       (coercion/coerce-response coercers request (handler request)))
                      ([request respond raise]
                       (handler request #(respond (coercion/coerce-response coercers request %)) raise)))))))})

(def coerce-exceptions-middleware
  "Middleware for handling coercion exceptions.
  Expects a :coercion of type `reitit.coercion/Coercion`
  and :parameters or :responses from route data, otherwise does not mount."
  {:name ::coerce-exceptions
   :compile (fn [{:keys [coercion parameters responses]} _]
              (if (and coercion (or parameters responses))
                (fn [handler]
                  (fn
                    ([request]
                     (try
                       (handler request)
                       (catch #?(:clj Exception :cljs js/Error) e
                         (handle-coercion-exception e identity #(throw %)))))
                    ([request respond raise]
                     (try
                       (handler request respond #(handle-coercion-exception % respond raise))
                       (catch #?(:clj Exception :cljs js/Error) e
                         (handle-coercion-exception e respond raise))))))))})
