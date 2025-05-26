# Data-driven Middleware

Ring [defines middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware) as a function of type `handler & args => request => response`. It is relatively easy to understand and allows for good performance. A downside is that the middleware chain is just a opaque function, making things like debugging and composition hard. It is too easy to apply the middlewares in wrong order.

For the basics of reitit middleware, [read this first](ring.md#middleware).

Reitit defines middleware as data:

1. A middleware can be defined as first-class data entries
2. A middleware can be mounted as a [duct-style](https://github.com/duct-framework/duct/wiki/Configuration) vector (of middlewares)
4. A middleware can be optimized & [compiled](compiling_middleware.md) against an endpoint
3. A middleware chain can be transformed by the router

## Middleware as data

All values in the `:middleware` vector of route data are expanded into `reitit.middleware/Middleware` Records by using the `reitit.middleware/IntoMiddleware` Protocol. By default, functions, maps and `Middleware` records are allowed.

Records can have arbitrary keys, but the following keys have special purpose:

| key            | description |
| ---------------|-------------|
| `:name`        | Name of the middleware as a qualified keyword
| `:spec`        | `clojure.spec` definition for the route data, see [route data validation](route_data_validation.md) (optional)
| `:wrap`        | The actual middleware function of `handler & args => request => response`
| `:compile`     | Middleware compilation function, see [compiling middleware](compiling_middleware.md).

Middleware Records are accessible in their raw form in the compiled route results, and thus are available for inventories, creating api-docs, etc.

For the actual request processing, the Records are unwrapped into normal functions and composed into a middleware function chain, yielding zero runtime penalty.

### Creating Middleware

The following examples produce identical middleware runtime functions.

### Function

```clj
(defn wrap [handler id]
  (fn [request]
    (handler (update request ::acc (fnil conj []) id))))
```

### Map

```clj
(def wrap3
  {:name ::wrap3
   :description "Middleware that does things."
   :wrap wrap})
```

### Record

```clj
(require '[reitit.middleware :as middleware])

(def wrap2
  (middleware/map->Middleware
    {:name ::wrap2
     :description "Middleware that does things."
     :wrap wrap}))
```

## Using Middleware

`:middleware` is merged to endpoints by the `router`.

```clj
(require '[reitit.ring :as ring])

(defn handler [{::keys [acc]}]
  {:status 200, :body (conj acc :handler)})

(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap 1] [wrap2 2]]}
       ["/ping" {:get {:middleware [[wrap3 3]]
                       :handler handler}}]])))
```

All the middlewares are applied correctly:

```clj
(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body [1 2 3 :handler]}
```

## Compiling middleware

Middlewares can be optimized against an endpoint using [middleware compilation](compiling_middleware.md).

## Ideas for the future

* Support Middleware dependency resolution with new keys `:requires` and `:provides`. Values are set of top-level keys of the request. e.g.
   * `InjectUserIntoRequestMiddleware` requires `#{:session}` and provides `#{:user}`
   * `AuthorizationMiddleware` requires `#{:user}`

Ideas welcome & see [issues](https://github.com/metosin/reitit/issues) for details.
