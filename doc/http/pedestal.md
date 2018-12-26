# Pedestal

[Pedestal](http://pedestal.io/) is a well known interceptor-based web framework for Clojure. To use `reitit-http` with Pedestal, we need to change the default routing interceptor. The needed helpers for this are found in a separate package:

```clj
[metosin/reitit-ring "0.2.9"]
```

You should read the [interceptor guide](interceptors.md) to understand the basics on Interceptor-based dispatch.

## Example

A minimalistic example on how to to swap the default-router with a reitit router.

```clj
; [io.pedestal/pedestal.service "0.5.5"]
; [io.pedestal/pedestal.jetty "0.5.5"]
; [metosin/reitit-pedestal "0.2.9"]
; [metosin/reitit "0.2.9"]

(ns example.server
  (:require [io.pedestal.http :as server]
            [reitit.pedestal :as pedestal]
            [reitit.http :as http]
            [reitit.ring :as ring]))

(def router
  (pedestal/routing-interceptor
    (http/router
      ["/ping" (fn [_] {:status 200, :body "pong"})])
    (ring/create-default-handler)))

(defn start []
  (-> {::server/type :jetty
       ::server/port 3000
       ::server/join? false
       ;; no pedestal routes
       ::server/routes []}
      (server/default-interceptors)
      ;; swap the reitit router
      (pedestal/replace-last-interceptor router)
      (server/dev-interceptors)
      (server/create-server)
      (server/start))
  (println "server running in port 3000"))

(start)
```

## Caveat

There is no common interceptor spec for Clojure and All default reitit interceptors (coercion, exceptions etc.) use the [Sieppari](https://github.com/metosin/sieppari) interceptor model. For most parts, they are fully compatible with the Pedestal Interceptor model. Only exception being that the `:error` handlers take just 1 arity (`context`) compared to [Pedestal's 2-arity](http://pedestal.io/reference/error-handling) (`context` and `exception`). 

Currently, there is only the `reitit.http.interceptors.exception/exception-interceptor` which has `:error` defined - just don't use it and everything should just work.

You are most welcome to discuss about a common interceptor spec in [#interceptors](https://clojurians.slack.com/messages/interceptors/) in [Clojurians Slack](http://clojurians.net/).

See the [error handling guide](http://pedestal.io/reference/error-handling) on how to handle errors with Pedestal.

## More examples

### Simple

Simple example, with both sync & async interceptors: https://github.com/metosin/reitit/tree/master/examples/pedestal

### Swagger

More complete example with custom interceptors, [default interceptors](default_interceptors.md), [coercion](../coercion/coercion.md) and [swagger](../ring/swagger.md)-support: https://github.com/metosin/reitit/tree/master/examples/pedestal-swagger

note: exception handling is disabled in this example
