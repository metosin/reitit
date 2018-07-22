(ns reitit.ring.middleware.exception
  (:require [reitit.coercion :as coercion]
            [reitit.ring :as ring]))

(defn- super-classes [^Class k]
  (loop [sk (.getSuperclass k), ks []]
    (if-not (= sk Object)
      (recur (.getSuperclass sk) (conj ks sk))
      ks)))

(defn- call-error-handler [default-handler handlers error request]
  (let [{:keys [type] :as data} (ex-data error)
        ex-class (class error)
        error-handler (or (get handlers type)
                          (get handlers ex-class)
                          (some
                            (partial get handlers)
                            (descendants type))
                          (some
                            (partial get handlers)
                            (super-classes ex-class))
                          default-handler)]
    (error-handler error data request)))

(defn default-handler [^Exception e _ _]
  {:status 500
   :body {:type "exception"
          :class (.getName (.getClass e))}})

(defn coercion-handler [status]
  (fn [_ data _]
    {:status status
     :body (coercion/encode-error data)}))

(defn http-response-handler
  "reads response from ex-data :response"
  [_ {:keys [response]} _]
  response)

(defn request-parsing-handler [_ {:keys [format]} _]
  {:status 400
   :headers {"Content-Type" "text/plain"}
   :body (str "Malformed " (pr-str format) " request.")})

;;
;; public api
;;

(def default-options
  {::default default-handler
   ::ring/response http-response-handler
   :muuntaja/decode request-parsing-handler
   ::coercion/request-coercion (coercion-handler 400)
   ::coercion/response-coercion (coercion-handler 500)})

(defn create-exceptions-middleware
  "Catches all exceptions and looks up a exception handler:
  1) `:type` of ex-data
  2) Class of Exception
  3) descadents `:type` of ex-data
  4) Super Classes of Exception
  5) The ::default handler"
  ([]
   (create-exceptions-middleware default-options))
  ([options]
   (let [default-handler (get options ::default default-handler)
         on-exception (fn [e request respond raise]
                        (try
                          (respond (call-error-handler default-handler options e request))
                          (catch Exception e
                            (raise e))))]
     {:name ::exception
      :wrap (fn [handler]
              (fn
                ([request]
                 (try
                   (handler request)
                   (catch Throwable e
                     (on-exception e request identity #(throw %)))))
                ([request respond raise]
                 (try
                   (handler request respond (fn [e] (on-exception e request respond raise)))
                   (catch Throwable e
                     (on-exception e request respond raise))))))})))
