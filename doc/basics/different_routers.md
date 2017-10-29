# Different Routers

Reitit ships with several different implementations for the `Router` protocol, originally based on the awesome [Pedestal](https://github.com/pedestal/pedestal/tree/master/route) implementation. `router` selects the most suitable implementation by inspecting the expanded routes. The implementation can be set manually using `:router` ROUTER OPTION.

| router                | description |
| ----------------------|-------------|
| `:linear-router`      | Matches the routes one-by-one starting from the top until a match is found. Works with any kind of routes.
| `:lookup-router`      | Fastest router, uses hash-lookup to resolve the route. Valid if no paths have path or catch-all parameters.
| `:mixed-router`       | Creates internally a `:linear-router` and a `:lookup-router` and used them to effectively get best-of-both-worlds. Valid if there are no CONFLICTING ROUTES.
| `:prefix-tree-router` | [TODO](https://github.com/julienschmidt/httprouter#how-does-it-work)

The router name can be asked from the router

```clj
(r/router-name router)
; :mixed-router
```
