# Pedestal

[Pedestal](http://pedestal.io/) is a backend web framework for Clojure. `reitit-pedestal` provides an alternative routing engine for Pedestal.

```clj
[metosin/reitit-pedestal "0.5.5"]
```

Why should one use reitit instead of the Pedestal [default routing](http://pedestal.io/reference/routing-quick-reference)?

* One simple [route syntax](../basics/route_syntax.md), with full [route conflict resolution](../basics/route_conflicts.md).
* Supports [first class route data](../basics/route_data.md) with [spec validation](../basics/route_data_validation.md).
* Fixes some [known problems](https://github.com/pedestal/pedestal/issues/532) in routing.
* Can handle [trailing backslashes](../ring/slash_handler.md).
* One router for both backend and [frontend](../frontend/basics.md).
* Supports [parameter coercion](../ring/coercion.md) & [Swagger](../ring/swagger.md).
* Is even [faster](../performance.md).

To use Pedestal with reitit, you should first read both the [Pedestal docs](http://pedestal.io/) and the [reitit interceptor guide](interceptors.md).


## Example

A minimalistic example on how to to swap the default-router with a reitit router.

```clj
; [io.pedestal/pedestal.service "0.5.5"]
; [io.pedestal/pedestal.jetty "0.5.5"]
; [metosin/reitit-pedestal "0.5.5"]
; [metosin/reitit "0.5.5"]

(require '[io.pedestal.http :as server])
(require '[reitit.pedestal :as pedestal])
(require '[reitit.http :as http])
(require '[reitit.ring :as ring])

(defn interceptor [number]
  {:enter (fn [ctx] (update-in ctx [:request :number] (fnil + 0) number))})

(def routes
  ["/api"
   {:interceptors [(interceptor 1)]}

   ["/number"
    {:interceptors [(interceptor 10)]
     :get {:interceptors [(interceptor 100)]
           :handler (fn [req]
                      {:status 200
                       :body (select-keys req [:number])})}}]])

(-> {::server/type :jetty
     ::server/port 3000
     ::server/join? false
     ;; no pedestal routes
     ::server/routes []}
    (server/default-interceptors)
    ;; swap the reitit router
    (pedestal/replace-last-interceptor
      (pedestal/routing-interceptor
        (http/router routes)))
    (server/dev-interceptors)
    (server/create-server)
    (server/start))
```

## Compatibility

There is no common interceptor spec for Clojure and all default reitit interceptors (coercion, exceptions etc.) use the [Sieppari](https://github.com/metosin/sieppari) interceptor model. It is mostly compatible with the Pedestal Interceptor model, only exception being that the `:error` handlers take just 1 arity (`context`) compared to [Pedestal's 2-arity](http://pedestal.io/reference/error-handling) (`context` and `exception`).

Currently, out of the reitit default interceptors, there is only the `reitit.http.interceptors.exception/exception-interceptor` which has the `:error` defined.

You are most welcome to discuss about a common interceptor spec in [#interceptors](https://clojurians.slack.com/messages/interceptors/) on [Clojurians Slack](http://clojurians.net/).

## More examples

### Simple

Simple example with sync & async interceptors: https://github.com/metosin/reitit/tree/master/examples/pedestal

### Swagger

More complete example with custom interceptors, [default interceptors](default_interceptors.md), [coercion](../coercion/coercion.md) and [swagger](../ring/swagger.md)-support enabled: https://github.com/metosin/reitit/tree/master/examples/pedestal-swagger
