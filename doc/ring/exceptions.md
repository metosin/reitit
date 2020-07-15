# Exception Handling with Ring

```clj
[metosin/reitit-middleware "0.5.5"]
```

Exceptions thrown in router creation can be [handled with custom exception handler](../basics/error_messages.md). By default, exceptions thrown at runtime from a handler or a middleware are not caught by the `reitit.ring/ring-handler`. A good practise is a have an top-level exception handler to log and format the errors for clients.

```clj
(require '[reitit.ring.middleware.exception :as exception])
```

### `exception/exception-middleware`

A preconfigured middleware using `exception/default-handlers`. Catches:

* Request & response [Coercion](coercion.md) exceptions
* [Muuntaja](https://github.com/metosin/muuntaja) decode exceptions
* Exceptions with `:type` of `:reitit.ring/response`, returning `:response` key from `ex-data`.
* Safely all other exceptions

```clj
(require '[reitit.ring :as ring])

(def app
  (ring/ring-handler
    (ring/router
      ["/fail" (fn [_] (throw (Exception. "fail")))]
      {:data {:middleware [exception/exception-middleware]}})))

(app {:request-method :get, :uri "/fail"})
;{:status 500
; :body {:type "exception"
;        :class "java.lang.Exception"}}
```

### `exception/create-exception-middleware`

Creates the exception-middleware with custom options. Takes a map of `identifier => exception request => response` that is used to select the exception handler for the thrown/raised exception identifier. Exception identifier is either a `Keyword` or a Exception Class.

The following handlers are available by default:

| key                                  | description
|--------------------------------------|-------------
| `:reitit.ring/response`              | value in ex-data key `:response` will be returned
| `:muuntaja/decode`                   | handle Muuntaja decoding exceptions
| `:reitit.coercion/request-coercion`  | request coercion errors (http 400 response)
| `:reitit.coercion/response-coercion` | response coercion errors (http 500 response)
| `::exception/default`                | a default exception handler if nothing else matched (default `exception/default-handler`).
| `::exception/wrap`                   | a 3-arity handler to wrap the actual handler `handler exception request => response` (no default).

The handler is selected from the options map by exception identifier in the following lookup order:

1) `:type` of exception ex-data
2) Class of exception
3) `:type` ancestors of exception ex-data
4) Super Classes of exception
5) The ::default handler

```clj
;; type hierarchy
(derive ::error ::exception)
(derive ::failure ::exception)
(derive ::horror ::exception)

(defn handler [message exception request]
  {:status 500
   :body {:message message
          :exception (.getClass exception)
          :data (ex-data exception)
          :uri (:uri request)}})

(def exception-middleware
  (exception/create-exception-middleware
    (merge
      exception/default-handlers
      {;; ex-data with :type ::error
       ::error (partial handler "error")

       ;; ex-data with ::exception or ::failure
       ::exception (partial handler "exception")

       ;; SQLException and all it's child classes
       java.sql.SQLException (partial handler "sql-exception")

       ;; override the default handler
       ::exception/default (partial handler "default")

       ;; print stack-traces for all exceptions
       ::exception/wrap (fn [handler e request]
                          (println "ERROR" (pr-str (:uri request)))
                          (handler e request))})))

(def app
  (ring/ring-handler
    (ring/router
      ["/fail" (fn [_] (throw (ex-info "fail" {:type ::failue})))]
      {:data {:middleware [exception-middleware]}})))

(app {:request-method :get, :uri "/fail"})
; ERROR "/fail"
; => {:status 500,
;     :body {:message "default"
;            :exception clojure.lang.ExceptionInfo
;            :data {:type :user/failue}
;            :uri "/fail"}}
```
