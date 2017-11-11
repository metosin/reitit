# Data-driven Middleware

Ring [defines middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware) as a function of type `handler & opts => request => response`. It's easy to undrstand and enables great performance, but makes the middleware-chain opaque, making things like documentation and debugging hard.

Reitit does things bit differently:

1. middleware is defined as a vector (of middleware) enabling the chain to be malipulated before turned into the optimized runtime chain.
2. middleware can be defined as first-class data entries

### Middleware as data

Everything that is defined inside the `:middleware` vector in the route data is coerced into `reitit.ring.middleware/Middleware` Records with the help of `reitit.ring.middleware/IntoMiddleware` Protocol. By default, it transforms functions, maps and `Middleware` records. For the actual

Records can have arbitrary keys, but the default keys have a special purpose:

| key         | description |
| ------------|-------------|
| `:name`     | Name of the middleware as a qualified keyword (optional)
| `:wrap`     | The actual middleware function of `handler & args => request => response`
| `:gen-wrap` | Middleware function generation function, see [compiling middleware](compiling_middleware.md).

Middleware Records are accessible in their raw form in the compiled route results, thus available for inventories, creating api-docs etc.

For the actual request processing, the Records are unwrapped into normal functions, yielding zero runtime penalty.

### Creating Middleware

The following produce identical middleware runtime function.

#### Function

```clj
(defn wrap [handler id]
  (fn [request]
    (handler (update request ::acc (fnil conj []) id))))
```

### Record

```clj
(require '[reitit.ring.middleware :as middleware])

(def wrap2
  (middleware/create
    {:name ::wrap2
     :description "Middleware that does things."
     :wrap wrap}))
```

#### Map

```clj
(def wrap3
  {:name ::wrap3
   :description "Middleware that does things."
   :wrap wrap})
```

### Using Middleware

```clj
(require '[reitit.ring :as ring])

(defn handler [{:keys [::acc]}]
  {:status 200, :body (conj acc :handler)})

(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap 1] [wrap2 2]]}
       ["/ping" {:get {:middleware [[wrap3 3]]
                       :handler handler}}]])))
```

All the middleware are called correctly:

```clj
(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body [1 2 3 :handler]}
```

### Future

Some things bubblin' under:

* Hooks to manipulate the `:middleware` chain before compilation
* Support `Keyword` expansion into Middleware, enabling external Middleware Registries (duct/integrant/macchiato -style)
* Support Middleware dependency resolution with new keys `:requires` and `:provides`. Values are set of top-level keys of the request. e.g.
   * `InjectUserIntoRequestMiddleware` requires `#{:session}` and provides `#{:user}`
   * `AuthorizationMiddleware` requires `#{:user}`
* Support partial `s/keys` route data specs with Middleware (and Router). Merged together to define sound spec for the route data and/or route data for a given route.
   * e.g. `AuthrorizationMiddleware` has a spec defining `:roles` key (a set of keywords)
   * Documentation for the route data
   * Route data is validated against the spec:
      * Complain of keywords that are not handled by anything
      * Propose fixes for typos (Figwheel-style)

Ideas welcome & see [issues](https://github.com/metosin/reitit/issues) for details.
