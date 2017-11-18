# Router

Routes are just data and for routing, we need a router instance satisfying the `reitit.core/Router` protocol. Routers are created with `reitit.core/router` function, taking the raw routes and optionally an options map.

The `Router` protocol:

```clj
(defprotocol Router
  (router-name [this])
  (routes [this])
  (options [this])
  (route-names [this])
  (match-by-path [this path])
  (match-by-name [this name] [this name params]))
```

Creating a router:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/api"
      ["/ping" ::ping]
      ["/user/:id" ::user]]]))
```

Name of the created router:

```clj
(r/router-name router)
; :mixed-router
```

The flattened route tree:

```clj
(r/routes router)
; [["/api/ping" {:name :user/ping}]
;  ["/api/user/:id" {:name :user/user}]]
```

### Behind the scenes
When router is created, the following steps are done:
* route tree is flattened
* route arguments are expanded (via `reitit.core/Expand` protocol) and optionally coerced
* [route conflicts](advanced/route_conflicts.md) are resolved
* actual [router implementation](../advanced/different_routers.md) is selected and created
* optionally route data gets compiled
