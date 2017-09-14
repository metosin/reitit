# Routers

For routing, a `Router` is needed. Reitit ships with several different router implementations: `:linear-router`, `:lookup-router` and `:mixed-router`, based on the awesome [Pedestal](https://github.com/pedestal/pedestal/tree/master/route) implementation.

`Router` is created with `reitit.core/router`, which takes routes and optional options map as arguments. The route tree gets expanded, optionally coerced and compiled. Actual `Router` implementation is selected automatically but can be defined with a `:router` option. `Router` support both path- and name-based lookups.

Creating a router:

```clj
(require '[reitit.core :as reitit])

(def router
  (reitit/router
    [["/api"
      ["/ping" ::ping]
      ["/user/:id" ::user]]]))
```

`:mixed-router` is created (both static & wild routes are found):

```clj
(reitit/router-name router)
; :mixed-router
```

The expanded routes:

```clj
(reitit/routes router)
; [["/api/ping" {:name :user/ping}]
;  ["/api/user/:id" {:name :user/user}]]
```

Route names:

```clj
(reitit/route-names router)
; [:user/ping :user/user]
```

### Path-based routing

```clj
(reitit/match-by-path router "/hello")
; nil

(reitit/match-by-path router "/api/user/1")
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :result nil
;        :params {:id "1"}}
```

### Name-based (reverse) routing

```clj
(reitit/match-by-name router ::user)
; #PartialMatch{:template "/api/user/:id",
;               :meta {:name :user/user},
;               :result nil,
;               :params nil,
;               :required #{:id}}

(reitit/partial-match? (reitit/match-by-name router ::user))
; true
```

Only a partial match. Let's provide the path-parameters:

```clj
(reitit/match-by-name router ::user {:id "1"})
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :result nil
;        :params {:id "1"}}
```

There is also a exception throwing version:

```clj
(reitit/match-by-name! router ::user)
; ExceptionInfo missing path-params for route /api/user/:id: #{:id}
```
