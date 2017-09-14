## Route conflicts

Route trees should not have multiple routes that match to a single (request) path. `router` checks the route tree at creation for conflicts and calls a registered `:conflicts` option callback with the found conflicts. Default implementation throws `ex-info` with a descriptive message.

```clj
(reitit/router
  [["/ping"]
   ["/:user-id/orders"]
   ["/bulk/:bulk-id"]
   ["/public/*path"]
   ["/:version/status"]])
; CompilerException clojure.lang.ExceptionInfo: router contains conflicting routes:
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
