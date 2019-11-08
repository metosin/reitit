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
; Router contains conflicting route paths:
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
; => #object[reitit.core$quarantine_router$reify]
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
;  => #object[reitit.core$quarantine_router$reify]
```

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
;CompilerException clojure.lang.ExceptionInfo: Router contains conflicting route names:
;
;:reitit.core/ping
;-> /ping
;-> /admin/ping
;
```

There is no way to disable the name conflict resolution.
