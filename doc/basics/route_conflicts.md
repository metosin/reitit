# Route Conflicts

Most routing libraries allow conflicting paths within a router. On lookup, the first match is used making rest of the matching routes effecively unreachable. This is not good, especially if route tree is merged from multiple sources.

Reitit resolves this by running explicit conflicit resolution when a Router is created. Conflicting routes are passed into a `:conflicts` callback. Default implementation throws `ex-info` with a descriptive message.

Examples router with conflicting routes:

```clj
(require '[reitit.core :as r])

(def routes
  [["/ping"]
   ["/:user-id/orders"]
   ["/bulk/:bulk-id"]
   ["/public/*path"]
   ["/:version/status"]])
```

By default, `ExceptionInfo` is thrown:

```clj
(r/router routes)
; CompilerException clojure.lang.ExceptionInfo: Router contains conflicting routes:
;
;    /:user-id/orders
; -> /public/*path
; -> /bulk/:bulk-id
;
;    /bulk/:bulk-id
; -> /:version/status
;
;    /public/*path
; -> /:version/status
;
```

Just logging the conflicts:

```clj
(r/router
  routes
  {:conflicts (comp println reitit/conflicts-str)})
; Router contains conflicting routes:
;
;    /:user-id/orders
; -> /public/*path
; -> /bulk/:bulk-id
;
;    /bulk/:bulk-id
; -> /:version/status
;
;    /public/*path
; -> /:version/status
;
```
