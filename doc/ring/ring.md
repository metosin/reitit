# Ring Router

[Ring](https://github.com/ring-clojure/ring) is a Clojure web applications library inspired by Python's WSGI and Ruby's Rack. By abstracting the details of HTTP into a simple, unified API, Ring allows web applications to be constructed of modular components that can be shared among a variety of applications, web servers, and web frameworks.

Read more about the [Ring Concepts](https://github.com/ring-clojure/ring/wiki/Concepts).

```clj
[metosin/reitit-ring "0.9.1"]
```

## `reitit.ring/router`

`reitit.ring/router` is a higher order router, which adds support for `:request-method` based routing, [handlers](https://github.com/ring-clojure/ring/wiki/Concepts#handlers) and [middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware).

It accepts the following options:

| key                                     | description |
| ----------------------------------------|-------------|
| `:reitit.middleware/transform`          | Function of `[Middleware] => [Middleware]` to transform the expanded Middleware (default: identity).
| `:reitit.middleware/registry`           | Map of `keyword => IntoMiddleware` to replace keyword references into Middleware
| `:reitit.ring/default-options-endpoint` | Default endpoint for `:options` method (default: default-options-endpoint)

Example router:

```clj
(require '[reitit.ring :as ring])

(defn handler [_]
  {:status 200, :body "ok"})

(def router
  (ring/router
    ["/ping" {:get handler}]))
```

Match contains `:result` compiled by `reitit.ring/router`:

```clj
(require '[reitit.core :as r])

(r/match-by-path router "/ping")
;#Match{:template "/ping"
;       :data {:get {:handler #object[...]}}
;       :result #Methods{:get #Endpoint{...}
;                        :options #Endpoint{...}}
;       :path-params {}
;       :path "/ping"}
```

## `reitit.ring/ring-handler`

Given a router from `reitit.ring/router`, optional default-handler & options, `ring-handler` function will return a valid ring handler supporting both synchronous and [asynchronous](https://www.booleanknot.com/blog/2016/07/15/asynchronous-ring.html) request handling. The following options are available:

| key               | description |
| ------------------|-------------|
| `:middleware`     | Optional sequence of middlewares that wrap the ring-handler
| `:inject-match?`  | Boolean to inject `match` into request under `:reitit.core/match` key (default true)
| `:inject-router?` | Boolean to inject `router` into request under `:reitit.core/router` key (default true)

Simple Ring app:

```clj
(def app (ring/ring-handler router))
```

Applying the handler:

```clj
(app {:request-method :get, :uri "/favicon.ico"})
; nil
```

```clj
(app {:request-method :get, :uri "/ping"})
; {:status 200, :body "ok"}
```

The router can be accessed via `get-router`:

```clj
(-> app (ring/get-router) (r/compiled-routes))
;[["/ping"
;  {:handler #object[...]}
;  #Methods{:get #Endpoint{:data {:handler #object[...]}
;                          :handler #object[...]
;                          :middleware []}
;           :options #Endpoint{:data {:handler #object[...]}
;                              :handler #object[...]
;                              :middleware []}}]]
```

# Request-method based routing

Handlers can be placed either to the top-level (all methods) or under a specific method (`:get`, `:head`, `:patch`, `:delete`, `:options`, `:post`, `:put` or `:trace`). Top-level handler is used if request-method based handler is not found.

By default, the `:options` route is generated for all paths - to enable thing like [CORS](https://en.wikipedia.org/wiki/Cross-origin_resource_sharing).

```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/all" handler]
       ["/ping" {:name ::ping
                 :get handler
                 :post handler}]])))
```

Top-level handler catches all methods:

```clj
(app {:request-method :delete, :uri "/all"})
; {:status 200, :body "ok"}
```

Method-level handler catches only the method:

```clj
(app {:request-method :get, :uri "/ping"})
; {:status 200, :body "ok"}

(app {:request-method :put, :uri "/ping"})
; nil
```

By default, `:options` is also supported (see router options to change this):

```clj
(app {:request-method :options, :uri "/ping"})
; {:status 200, :body ""}
```

Name-based reverse routing:

```clj
(-> app
    (ring/get-router)
    (r/match-by-name ::ping)
    (r/match->path))
; "/ping"
```

# Middleware

Middleware can be mounted using a `:middleware` key in [Route Data](../basics/route_data.md) - either to top-level or under request method submap. Its value should be a vector of `reitit.middleware/IntoMiddleware` values. These include:

1. normal ring middleware function `handler -> request -> response`
2. vector of middleware function `[handler args*] -> request -> response` and it's arguments
3. a [data-driven middleware](data_driven_middleware.md) record or a map
4. a Keyword name, to lookup the middleware from a [Middleware Registry](middleware_registry.md)

A middleware and a handler:

```clj
(defn wrap [handler id]
  (fn [request]
    (handler (update request ::acc (fnil conj []) id))))

(defn handler [{::keys [acc]}]
  {:status 200, :body (conj acc :handler)})
```

App with nested middleware:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ;; a middleware function
      ["/api" {:middleware [#(wrap % :api)]}
       ["/ping" handler]
       ;; a middleware vector at top level
       ["/admin" {:middleware [[wrap :admin]]}
        ["/db" {:middleware [[wrap :db]]
                ;; a middleware vector at under a method
                :delete {:middleware [[wrap :delete]]
                         :handler handler}}]]])))
```

Middleware is applied correctly:

```clj
(app {:request-method :delete, :uri "/api/ping"})
; {:status 200, :body [:api :handler]}
```

```clj
(app {:request-method :delete, :uri "/api/admin/db"})
; {:status 200, :body [:api :admin :db :delete :handler]}
```

Top-level middleware, applied before any routing is done:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap :api]]}
       ["/get" {:get handler}]])
    nil
    {:middleware [[wrap :top]]}))

(app {:request-method :get, :uri "/api/get"})
; {:status 200, :body [:top :api :ok]}
```

Same middleware for all routes, using [top-level route data](route_data.md#top-level-route-data):

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/get" {:get handler
                :middleware [[wrap :specific]]}]]
      {:data {:middleware [[wrap :generic]]}})))

(app {:request-method :get, :uri "/api/get"})
; {:status 200, :body [:generic :specific :handler]}
```

## Execution order

Here's a full example that shows the execution order of the middleware
using all of the above techniques:


```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap :3-parent]]}
       ["/get" {:get handler
                :middleware [[wrap :4-route]]}]]
      {:data {:middleware [[wrap :2-top-level-route-data]]}})
    nil
    {:middleware [[wrap :1-top]]}))

(app {:request-method :get, :uri "/api/get"})
; {:status 200, :body [:1-top :2-top-level-route-data :3-parent :4-route :handler]}
```

## Which method should I use for defining middleware?

- If you have middleware that you want to apply to the default handler (second argument of `ring/ring-handler`), use _top-level middleware_
- If you have a generic middleware, that doesn't depend on the route, use _top-level middleware_ or _top-level route data_
  - If you are using top-level route data anyway for some other reasons, it might be clearest to have all the middleware there. This is what most of the reitit examples do.
- If you want to apply a middleware to only a couple of routes, use _nested middleware_ (ie. _route data_)
- If you want a middleware to apply to all routes, but use route-specific data, you need _top-level route data_ combined with [Compiling Middleware](compiling_middleware.md)
  - This is what many reitit features like [Ring Coercion](coercion.md) do. Check the examples & docs for the reitit features you want to use!
