# Route conflicts

Many routing libraries allow single path lookup could match multiple routes. Usually, first match is used. This is not good, especially if route tree is merged from multiple sources - routes might regress to be unreachable without a warning.

Reitit resolves this by running explicit conflicit resolution when a `Router` is created. Conflicting routes are passed into a `:conflicts` callback. Default implementation throws `ex-info` with a descriptive message.

Examples routes with conflicts:

```clj
(require '[reitit.core :as reitit])

(def routes
  [["/ping"]
   ["/:user-id/orders"]
   ["/bulk/:bulk-id"]
   ["/public/*path"]
   ["/:version/status"]])
```

By default, `ExceptionInfo` is thrown:

```clj
(reitit/router routes)
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
(reitit/router
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
