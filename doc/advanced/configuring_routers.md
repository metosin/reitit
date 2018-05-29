# Configuring Routers

Routers can be configured via options. The following options are available for the `reitit.core/router`:

  | key          | description |
  |--------------|-------------|
  | `:path`      | Base-path for routes |
  | `:routes`    | Initial resolved routes (default `[]`) |
  | `:data`      | Initial route data (default `{}`) |
  | `:spec`      | clojure.spec definition for a route data, see `reitit.spec` on how to use this |
  | `:expand`    | Function of `arg opts => data` to expand route arg to route data (default `reitit.core/expand`) |
  | `:coerce`    | Function of `route opts => route` to coerce resolved route, can throw or return `nil` |
  | `:compile`   | Function of `route opts => result` to compile a route handler |
  | `:validate`  | Function of `routes opts => ()` to validate route (data) via side-effects |
  | `:conflicts` | Function of `{route #{route}} => ()` to handle conflicting routes (default `reitit.core/throw-on-conflicts!`) |
  | `:router`    | Function of `routes opts => router` to override the actual router implementation |
