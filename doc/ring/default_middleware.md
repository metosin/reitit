# Default Middleware

```clj
[metosin/reitit-middleware "0.2.0-SNAPSHOT"]
```

Any Ring middleware can be used with `reitit-ring`, but using data-driven middleware is preferred as they are easier to manage and in many cases, yield better performance. `reitit-middleware` contains a set of common ring middleware, lifted into data-driven middleware.

* [Exception handling](#exception-handling)
* [Content negotiation](#content-negotiation)
* [Multipart request handling](#multipart-request-handling)

## Exception handling

A polished version of [compojure-api](https://github.com/metosin/compojure-api) exception handling. Catches all exceptions and invokes configured exception handler.

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

Creates the exception-middleware with custom options. Takes a map of `identifier => exception request => response` that is used to select the exception handler for the thown/raised exception identifier. Exception idenfier is either a `Keyword` or a Exception Class.

The following handlers special keys are available:

| key          | description
|--------------|-------------
| `::default`  | a default exception handler if nothing else mathced (default `exception/default-handler`).
| `::wrap`     | a 3-arity handler to wrap the actual handler `handler exception request => response` (no default).

The handler is selected from the options map by exception idenfitifier in the following lookup order:

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

## Content Negotiation

Wrapper for [Muuntaja](https://github.com/metosin/muuntaja) middleware for content-negotiation, request decoding and response encoding. Reads configuration from route data and emit's [swagger](swagger.md) `:produces` and `:consumes` definitions automatically.

```clj
(require '[reitit.ring.middleware.muuntaja :as muuntaja])
```

## Multipart request handling

Wrapper for [Ring Multipart Middleware](https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/multipart_params.clj). Conditionally mounts to an endpoint only if it has `:multipart` params defined. Emits swagger `:consumes` definitions automatically.

```clj
(require '[reitit.ring.middleware.multipart :as multipart])
```

## Example app

See an example app with the default middleware in action: https://github.com/metosin/reitit/blob/master/examples/ring-swagger/src/example/server.clj.
