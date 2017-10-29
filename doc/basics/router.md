# Router

Routes are just data and to do actual routing, we need a Router satisfying the `reitit.core/Router` protocol. Routers are created with `reitit.core/router` function, taking the raw routes and optionally an options map. Raw routes gets expanded and optionally coerced and compiled.

`Router` protocol:

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

Router flattens the raw routes and expands the route arguments using `reitit.core/Expand` protocol. By default, `Keyword`s are expanded to `:name` and functions are expaned to `:handler`. `nil` routes are removed. The expanded routes can be retrieved with router:

```clj
(r/routes router)
; [["/api/ping" {:name :user/ping}]
;  ["/api/user/:id" {:name :user/user}]]
```
