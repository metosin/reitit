(ns reitit.ring.middleware.exception
  (:require [reitit.coercion :as coercion]
            [reitit.ring :as ring]
            [clojure.spec.alpha :as s]))

(s/def ::handlers (s/map-of any? fn?))
(s/def ::spec (s/keys :opt-un [::handlers]))

;;
;; helpers
;;

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

(defn- wrap [{:keys [handlers]}]
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

(def default-options
  {:handlers {::default default-handler
              ::ring/response http-response-handler
              :muuntaja/decode request-parsing-handler
              ::coercion/request-coercion (create-coercion-handler 400)
              ::coercion/response-coercion (create-coercion-handler 500)}})

(defn wrap-exception
  "Ring middleware that catches all exceptions and looks up a
  exceptions handler of type `exception request => response` to
  handle the exception.

  The following options are supported:

  | key          | description
  |--------------|-------------
  | `:handlers`  | A map of exception identifier => exception-handler

  The handler is selected from the handlers by exception idenfiter
  in the following lookup order:

  1) `:type` of exception ex-data
  2) Class of exception
  3) descadents `:type` of exception ex-data
  4) Super Classes of exception
  5) The ::default handler"
  [handler options]
  (-> options wrap handler))

(def exception-middleware
  "Reitit middleware that catches all exceptions and looks up a
  exceptions handler of type `exception request => response` to
  handle the exception.

  The following options are supported:

  | key          | description
  |--------------|-------------
  | `:handlers`  | A map of exception identifier => exception-handler

  The handler is selected from the handlers by exception idenfiter
  in the following lookup order:

  1) `:type` of exception ex-data
  2) Class of exception
  3) descadents `:type` of exception ex-data
  4) Super Classes of exception
  5) The ::default handler"
  {:name ::exception
   :spec ::spec
   :wrap (wrap default-options)})

(defn create-exception-middleware
  "Creates a reitit middleware that catches all exceptions and looks up a
  exceptions handler of type `exception request => response` to
  handle the exception.

  The following options are supported:

  | key          | description
  |--------------|-------------
  | `:handlers`  | A map of exception identifier => exception-handler

  The handler is selected from the handlers by exception idenfiter
  in the following lookup order:

  1) `:type` of exception ex-data
  2) Class of exception
  3) descadents `:type` of exception ex-data
  4) Super Classes of exception
  5) The ::default handler"
  ([]
   (create-exception-middleware default-options))
  ([options]
   {:name ::exception
    :spec ::spec
    :wrap (wrap options)}))
