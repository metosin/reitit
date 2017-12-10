# Route Conflicts

Many routing libraries allow multiple matches for a single path lookup. Usually, the first match is used and the rest are effecively unreachanle. This is not good, especially if route tree is merged from multiple sources.

Reitit resolves this by running explicit conflicit resolution when a `router` is called. Conflicting routes are passed into a `:conflicts` callback. Default implementation throws `ex-info` with a descriptive message.

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
