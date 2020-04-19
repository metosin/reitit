# Router

Routes are just data and to do routing, we need a router instance satisfying the `reitit.core/Router` protocol. Routers are created with `reitit.core/router` function, taking the raw routes and optionally an options map.

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
    ["/api"
     ["/ping" ::ping]
     ["/user/:id" ::user]]))
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

With a router instance, we can do [Path-based routing](path_based_routing.md) or [Name-based (Reverse) routing](name_based_routing.md).

## More details

Router options:

```clj
(r/options router)
{:lookup #object[...]
 :expand #object[...]
 :coerce #object[...]
 :compile #object[...]
 :conflicts #object[...]}
```

Route names:

```clj
(r/route-names router)
; [:user/ping :user/user]
```

The compiled route tree:

```clj
(r/routes router)
; [["/api/ping" {:name :user/ping} nil]
;  ["/api/user/:id" {:name :user/user} nil]]
```

### Composing

As routes are defined as plain data, it's easy to merge multiple route trees into a single router

```clj
(def user-routes
  [["/users" ::users]
   ["/users/:id" ::user]]) 

(def admin-routes
  ["/admin"
   ["/ping" ::ping]
   ["/db" ::db]])

(r/router
  [admin-routes
   user-routes])
```

Merged route tree:

```clj
(r/routes router)
; [["/admin/ping" {:name :user/ping}]
;  ["/admin/db" {:name :user/db}]
;  ["/users" {:name :user/users}]
;  ["/users/:id" {:name :user/user}]]
``` 

More details on [composing routers](../advanced/composing_routers.md).

### Behind the scenes

When router is created, the following steps are done:
* route tree is flattened
* route arguments are expanded (via `:expand` option)
* routes are coerced (via `:coerce` options)
* route tree is compiled (via `:compile` options)
* [route conflicts](advanced/route_conflicts.md) are resolved (via `:conflicts` options)
* optionally, route data is validated (via `:validate` options)
* [router implementation](../advanced/different_routers.md) is automatically selected (or forced via `:router` options) and created
