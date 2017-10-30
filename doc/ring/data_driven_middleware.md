# Data-driven Middleware

Reitit supports first-class data-driven middleware via `reitit.ring.middleware/Middleware` records, created with `reitit.ring.middleware/create` function. The following keys have special purpose:

| key        | description |
| -----------|-------------|
| `:name`    | Name of the middleware as qualified keyword (optional,recommended for libs)
| `:wrap`    | The actual middleware function of `handler args? => request => response`
| `:gen`     | Middleware compile function, see [compiling middleware](compiling_middleware.md).

When routes are compiled, all middleware are expanded (and optionally compiled) into `Middleware` Records and stored in compilation results for later use (api-docs etc). For actual request processing, they are unwrapped into normal middleware functions and composed together producing zero runtime performance penalty. Middleware expansion is backed by `reitit.middleware/IntoMiddleware` protocol, enabling plain clojure(script) maps to be used.

A Record:

```clj
(require '[reitit.middleware :as middleware])

(def wrap2
  (middleware/create
    {:name ::wrap2
     :description "a nice little mw, takes 1 arg."
     :wrap wrap}))
```

As plain map:

```clj
;; plain map
(def wrap3
  {:name ::wrap3
   :description "a nice little mw, :api as arg"
   :wrap (fn [handler]
           (wrap handler :api))})
```

### TODO

more!
