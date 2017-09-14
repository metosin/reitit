## Configuring Routers

Routers can be configured via options. Options allow things like [`clojure.spec`](https://clojure.org/about/spec) validation for meta-data and fast, compiled handlers. The following options are available for the `reitit.core/router`:

  | key          | description |
  | -------------|-------------|
  | `:path`      | Base-path for routes
  | `:routes`    | Initial resolved routes (default `[]`)
  | `:meta`      | Initial expanded route-meta vector (default `[]`)
  | `:expand`    | Function of `arg opts => meta` to expand route arg to route meta-data (default `reitit.core/expand`)
  | `:coerce`    | Function of `route opts => route` to coerce resolved route, can throw or return `nil`
  | `:compile`   | Function of `route opts => result` to compile a route handler
  | `:conflicts` | Function of `{route #{route}} => side-effect` to handle conflicting routes (default `reitit.core/throw-on-conflicts!`)
  | `:router`    | Function of `routes opts => router` to override the actual router implementation

