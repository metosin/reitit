# Default Middleware

```clj
[metosin/reitit-middleware "0.2.4"]
```

Any Ring middleware can be used with `reitit-ring`, but using data-driven middleware is preferred as they are easier to manage and in many cases, yield better performance. `reitit-middleware` contains a set of common ring middleware, lifted into data-driven middleware.

* [Parameter handling](#parameters-handling)
* [Exception handling](#exception-handling)
* [Content negotiation](#content-negotiation)
* [Multipart request handling](#multipart-request-handling)

## Parameters handling

`reitit.ring.middleware.parameters/parameters-middleware` to capture query- and form-params. Wraps
`ring.middleware.params/wrap-params`.

**NOTE**: will be factored into two parts: a query-parameters middleware and a Muuntaja format responsible for the the `application/x-www-form-urlencoded` body format.

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

| key                    | description
|------------------------|-------------
| `::exception/default`  | a default exception handler if nothing else mathced (default `exception/default-handler`).
| `::exception/wrap`     | a 3-arity handler to wrap the actual handler `handler exception request => response` (no default).

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

Wrapper for [Muuntaja](https://github.com/metosin/muuntaja) middleware for content-negotiation, request decoding and response encoding. Takes explicit configuration via `:muuntaja` key in route data. Emit's [swagger](swagger.md) `:produces` and `:consumes` definitions automatically based on the Muuntaja configuration.

Negotiates a request body based on `Content-Type` header and response body based on `Accept`, `Accept-Charset` headers. Publishes the negotiation results as `:muuntaja/request` and `:muuntaja/response` keys into the request.
 
Decodes the request body into `:body-params` using the `:muuntaja/request` key in request if the `:body-params` doesn't already exist.
 
Encodes the response body using the `:muuntaja/response` key in request if the response doesn't have `Content-Type` header already set.

Expected route data:
 
| key          | description |
| -------------|-------------|
| `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set.

```clj
(require '[reitit.ring.middleware.muuntaja :as muuntaja])
```

* `muuntaja/format-middleware` - Negotiation, request decoding and response encoding in a single Middleware
* `muuntaja/format-negotiate-middleware` - Negotiation
* `muuntaja/format-request-middleware` - Request decoding
* `muuntaja/format-response-middleware` - Response encoding

```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.coercion :as rrc])
(require '[reitit.coercion.spec :as rcs])
(require '[ring.adapter.jetty :as jetty])

(def app
  (ring/ring-handler
    (ring/router
      [["/math"
        {:post {:summary "negotiated request & response (json, edn, transit)"
                :parameters {:body {:x int?, :y int?}}
                :responses {200 {:body {:total int?}}}
                :handler (fn [{{{:keys [x y]} :body} :parameters}]
                           {:status 200
                            :body {:total (+ x y)}})}}]
       ["/xml"
        {:get {:summary "forced xml response"
               :handler (fn [_]
                          {:status 200
                           :headers {"Content-Type" "text/xml"}
                           :body "<kikka>kukka</kikka>"})}}]]
      {:data {:muuntaja m/instance
              :coercion rcs/coercion
              :middleware [muuntaja/format-middleware
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))

(jetty/run-jetty #'app {:port 3000, :join? false})
```

Testing with [httpie](https://httpie.org/):

```bash
> http POST :3000/math x:=1 y:=2

HTTP/1.1 200 OK
Content-Length: 11
Content-Type: application/json; charset=utf-8
Date: Wed, 22 Aug 2018 16:59:54 GMT
Server: Jetty(9.2.21.v20170120)

{
 "total": 3
}
```

```bash
> http :3000/xml

HTTP/1.1 200 OK
Content-Length: 20
Content-Type: text/xml
Date: Wed, 22 Aug 2018 16:59:58 GMT
Server: Jetty(9.2.21.v20170120)

<kikka>kukka</kikka>
```

## Multipart request handling

Wrapper for [Ring Multipart Middleware](https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/multipart_params.clj). Emits swagger `:consumes` definitions automatically.

Expected route data:
 
| key          | description |
| -------------|-------------|
| `[:parameters :multipart]`  | mounts only if defined for a route.


```clj
(require '[reitit.ring.middleware.multipart :as multipart])
```

* `multipart/multipart-middleware` a preconfigured middleware for multipart handling
* `multipart/create-multipart-middleware` to generate with custom configuration

## Example app

See an example app with the default middleware in action: https://github.com/metosin/reitit/blob/master/examples/ring-swagger/src/example/server.clj.
