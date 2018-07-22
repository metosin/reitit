(ns reitit.ring.middleware.exception
  (:require [reitit.coercion :as coercion]
            [reitit.ring :as ring]))

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

(defn- super-classes [^Class k]
  (loop [sk (.getSuperclass k), ks []]
    (if-not (= sk Object)
      (recur (.getSuperclass sk) (conj ks sk))
      ks)))

(defn- call-error-handler [handlers error request]
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
                          (get handlers ::default default-handler))]
    (error-handler error data request)))

(defn- on-exception [handlers e request respond raise]
  (try
    (respond (call-error-handler handlers e request))
    (catch Exception e
      (raise e))))

(defn- wrap [options]
  (fn [handler]
    (fn
      ([request]
       (try
         (handler request)
         (catch Throwable e
           (on-exception options e request identity #(throw %)))))
      ([request respond raise]
       (try
         (handler request respond (fn [e] (on-exception options e request respond raise)))
         (catch Throwable e
           (on-exception options e request respond raise)))))))

;;
;; public api
;;

(def default-options
  {::default default-handler
   ::ring/response http-response-handler
   :muuntaja/decode request-parsing-handler
   ::coercion/request-coercion (coercion-handler 400)
   ::coercion/response-coercion (coercion-handler 500)})

(def exception-middleware
  "Catches all exceptions and looks up a exception handler:
  1) `:type` of ex-data
  2) Class of Exception
  3) descadents `:type` of ex-data
  4) Super Classes of Exception
  5) The ::default handler"
  {:name ::exception
   :wrap (wrap default-options)})

(defn create-exception-middleware
  ([]
   (create-exception-middleware default-options))
  ([options]
   {:name ::exception
    :wrap (wrap options)}))
