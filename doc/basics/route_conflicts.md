# Route Conflicts

We should fail fast if a router contains conflicting paths or route names.

When a `Router` is created via `reitit.core/router`, both path and route name conflicts are checked automatically. By default, in case of conflict, an `ex-info` is thrown with a descriptive message. In some (legacy api) cases, path conflicts should be allowed and one can override the path conflict resolution via `:conflicts` router option or via `:conflicting` route data.

## Path Conflicts

Routes with path conflicts:

```clj
(require '[reitit.core :as r])

(def routes
  [["/ping"]
   ["/:user-id/orders"]
   ["/bulk/:bulk-id"]
   ["/public/*path"]
   ["/:version/status"]])
```

Creating router with defaults:

```clj
(r/router routes)
;; =thrown-match=> {:type :path-conflicts}
;
; CompilerException clojure.lang.ExceptionInfo: Router contains conflicting route paths:
;
; -> /:user-id/orders
; -> /public/*path
; -> /bulk/:bulk-id
;
; -> /bulk/:bulk-id
; -> /:version/status
;
; -> /public/*path
; -> /:version/status
;
```

To ignore the conflicts:

```clj
(r/router
  routes
  {:conflicts nil})
; => #object[reitit.core$quarantine_router$reify
```

To just log the conflicts:

```clj
(require '[reitit.exception :as exception])

(r/router
  routes
  {:conflicts (fn [conflicts]
                (println (exception/format-exception :path-conflicts nil conflicts)))})
;; Router contains conflicting route paths:
;;
;; -> /:user-id/orders
;; -> /public/*path
;; -> /bulk/:bulk-id
;;
;; -> /bulk/:bulk-id
;; -> /:version/status
;;
;; -> /public/*path
;; -> /:version/status
```

Alternatively, you can ignore conflicting paths individually via `:conflicting` in route data:

```clj
(def routes
  [["/ping"]
   ["/:user-id/orders" {:conflicting true}]
   ["/bulk/:bulk-id" {:conflicting true}]
   ["/public/*path" {:conflicting true}]
   ["/:version/status" {:conflicting true}]])
; => #'user/routes
(r/router routes)
; => #object[reitit.core$quarantine_router$reify]
```

## How conflicting routes are matched

When path conflicts are allowed and no `:router` option is given, the router is created as a `:quarantine-router`. At creation time, it splits the routes into two groups:

* routes that don't conflict with any other route are served by a `:mixed-router`, which uses a lookup table for static paths and a prefix tree for paths with parameters (see [Different Routers](../advanced/different_routers.md))
* routes that conflict with at least one other route are served by a `:linear-router`, which tries the routes one by one

`match-by-path` first tries the non-conflicting routes, then the conflicting ones in the order they are defined in the route tree. The first match wins.

```clj
(def router
  (r/router
    [["/ping" ::ping]
     ["/users/:id" ::user]
     ["/users/active" ::active-users]
     ["/users/:id/orders" ::user-orders]]
    {:conflicts nil}))

(r/router-name router)
;; => :quarantine-router
```

`/users/:id` and `/users/active` conflict with each other. `/ping` and `/users/:id/orders` don't conflict with anything, so they are still served by the `:mixed-router`.

As `/users/:id` is defined first, it matches `/users/active` too, and the `::active-users` route is never matched by path:

```clj
(:template (r/match-by-path router "/users/active"))
;; => "/users/:id"

(:template (r/match-by-path router "/users/1"))
;; => "/users/:id"
```

To match the more specific route, define it first:

```clj
(def router
  (r/router
    [["/ping" ::ping]
     ["/users/active" ::active-users]
     ["/users/:id" ::user]
     ["/users/:id/orders" ::user-orders]]
    {:conflicts nil}))

(:template (r/match-by-path router "/users/active"))
;; => "/users/active"

(:template (r/match-by-path router "/users/1"))
;; => "/users/:id"
```

The order is the order of the flattened routes, as returned by `r/routes`.

With `reitit.ring`, the route is chosen by path only. If the first matching route has no handler for the request method, the request goes to the default handler (e.g. `405 Method Not Allowed` with `ring/create-default-handler`). It does not fall through to the next conflicting route.

Since route names can't conflict, the lookup order doesn't matter for `match-by-name`.

### Performance

Only the conflicting routes pay the cost of linear matching. Matching a path that is served by a non-conflicting route is about as fast as without any conflicts. Matching a conflicting route, or a path that matches no route at all, is slower, and gets slower the more conflicting routes there are. So it's fine to have a few conflicting routes in a large route tree. See also [Performance](../performance.md).

## Name conflicts

Routes with name conflicts:

```clj
(def routes
  [["/ping" ::ping]
   ["/admin" ::admin]
   ["/admin/ping" ::ping]])
```

Creating router with defaults:

```clj
(r/router routes)
;; =thrown-match=> {:type :name-conflicts}
;
;CompilerException clojure.lang.ExceptionInfo: Router contains conflicting route names:
;
;:reitit.core/ping
;-> /ping
;-> /admin/ping
;
```

There is no way to disable the name conflict resolution.
