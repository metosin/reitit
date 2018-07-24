(ns reitit.ring.middleware.exception
  (:require [reitit.coercion :as coercion]
            [reitit.ring :as ring]))

(defn- super-classes [^Class k]
  (loop [sk (.getSuperclass k), ks []]
    (if-not (= sk Object)
      (recur (.getSuperclass sk) (conj ks sk))
      ks)))

(defn- call-error-handler [handlers error request]
  (let [type (:type (ex-data error))
        ex-class (class error)
        error-handler (or (get handlers type)
                          (get handlers ex-class)
                          (some
                            (partial get handlers)
                            (descendants type))
                          (some
                            (partial get handlers)
                            (super-classes ex-class))
                          (get handlers ::default))]
    (error-handler error request)))

(defn- on-exception [handlers e request respond raise]
  (try
    (respond (call-error-handler handlers e request))
    (catch Exception e
      (raise e))))

;;
;; handlers
;;

(defn default-handler
  "Default safe handler for any exception."
  [^Exception e _]
  {:status 500
   :body {:type "exception"
          :class (.getName (.getClass e))}})

(defn create-coercion-handler
  "Creates a coercion exception handler."
  [status]
  (fn [e _]
    {:status status
     :body (coercion/encode-error (ex-data e))}))

(defn http-response-handler
  "Reads response from Exception ex-data :response"
  [e _]
  (-> e ex-data :response))

(defn request-parsing-handler [e _]
  {:status 400
   :headers {"Content-Type" "text/plain"}
   :body (str "Malformed " (-> e ex-data :format pr-str) " request.")})

;;
;; public api
;;

(def default-handlers
  {::default default-handler
   ::ring/response http-response-handler
   :muuntaja/decode request-parsing-handler
   ::coercion/request-coercion (create-coercion-handler 400)
   ::coercion/response-coercion (create-coercion-handler 500)})

(defn wrap-exception [handlers]
  (fn [handler]
    (fn
      ([request]
       (try
         (handler request)
         (catch Throwable e
           (on-exception handlers e request identity #(throw %)))))
      ([request respond raise]
       (try
         (handler request respond (fn [e] (on-exception handlers e request respond raise)))
         (catch Throwable e
           (on-exception handlers e request respond raise)))))))

(def exception-middleware
  "Middleware that catches all exceptions and looks up a exception handler
  from a [[default-handlers]] map in the lookup order:

  1) `:type` of ex-data
  2) Class of Exception
  3) descadents `:type` of ex-data
  4) Super Classes of Exception
  5) The ::default handler"
  {:name ::exception
   :wrap (wrap-exception default-handlers)})

(defn create-exception-middleware
  "Creates a middleware that catches all exceptions and looks up a exception handler
  from a given map of `handlers` with keyword or Exception class as keys and a 2-arity
  Exception handler function as values.

  1) `:type` of ex-data
  2) Class of Exception
  3) descadents `:type` of ex-data
  4) Super Classes of Exception
  5) The ::default handler"
  ([]
   (create-exception-middleware default-handlers))
  ([handlers]
   {:name ::exception
    :wrap (wrap-exception handlers)}))
