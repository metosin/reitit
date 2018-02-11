# Path-based Routing

Path-based routing is done using the `reitit.core/match-by-path` function. It takes the router and path as arguments and returns one of the following:

* `nil`, no match
* `PartialMatch`, path matched, missing path-parameters (only in reverse-routing)
* `Match`, an exact match

Given a router:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    ["/api"
     ["/ping" ::ping]
     ["/user/:id" ::user]]))
```

No match returns `nil`:

```clj
(r/match-by-path router "/hello")
; nil
```

Match provides the route information:

```clj
(r/match-by-path router "/api/user/1")
; #Match{:template "/api/user/:id"
;        :data {:name :user/user}
;        :path "/api/user/1"
;        :result nil
;        :path-params {:id "1"}}
```
