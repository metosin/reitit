# Different Routers

Reitit ships with several different implementations for the `Router` protocol, originally based on the [Pedestal](https://github.com/pedestal/pedestal/tree/master/route) implementation. `router` function selects the most suitable implementation by inspecting the expanded routes. The implementation can be set manually using `:router` option, see [configuring routers](advanced/configuring_routers.md).

| router                        | description |
| ------------------------------|-------------|
| `:linear-router`              | Matches the routes one-by-one starting from the top until a match is found. Slow, but works with all route trees.
| `:trie-router`                | Router that creates a optimized [search trie](https://en.wikipedia.org/wiki/Trie) out of an route table. Much faster than `:linear-router` for wildcard routes. Valid only if there are no [Route conflicts](../basics/route_conflicts.md).
| `:lookup-router`              | Fast router, uses hash-lookup to resolve the route. Valid if no paths have path or catch-all parameters and there are no [Route conflicts](../basics/route_conflicts.md).
| `:single-static-path-router`  | Super fast router: string-matches a route. Valid only if there is one static route.
| `:mixed-router`               | Contains two routers: `:trie-router` for wildcard routes and a `:lookup-router` or `:single-static-path-router` for static routes. Valid only if there are no [Route conflicts](../basics/route_conflicts.md).
| `:quarantine-router`          | Contains two routers: `:mixed-router` for non-conflicting routes and a `:linear-router` for conflicting routes.

The router name can be asked from the router:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/ping" ::ping]
     ["/api/:users" ::users]]))

(r/router-name router)
; :mixed-router
```

Overriding the router implementation:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/ping" ::ping]
     ["/api/:users" ::users]]
    {:router r/linear-router}))

(r/router-name router)
; :linear-router
```
