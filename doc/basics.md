# Route Syntax

Raw routes are defined as vectors, which have a String path, optional (non-sequential) route argument and optional child routes. Routes can be wrapped in vectors and lists and `nil` routes are ignored. Paths can have path-parameters (`:id`) or catch-all-parameters (`*path`).

Simple route:

```clj
["/ping"]
```

Two routes:

```clj
[["/ping"]
 ["/pong"]]
```

Routes with route arguments:

```clj
[["/ping" ::ping]
 ["/pong" {:name ::pong}]]
```

Routes with path parameters:

```clj
[["/users/:user-id"]
 ["/api/:version/ping"]]
```

Route with catch-all parameter:

```clj
["/public/*path"]
```

Nested routes:

```clj
["/api"
 ["/admin" {:middleware [::admin]}
  ["" ::admin]
  ["/db" ::db]]
 ["/ping" ::ping]]
```

Same routes flattened:

```clj
[["/api/admin" {:middleware [::admin], :name ::admin}]
 ["/api/admin/db" {:middleware [::admin], :name ::db}]
 ["/api/ping" {:name ::ping}]]
```

As routes are just data, it's easy to create them programamtically:

```clj
(defn cqrs-routes [actions dev-mode?]
  ["/api" {:interceptors [::api ::db]}
   (for [[type interceptor] actions
         :let [path (str "/" (name interceptor))
               method (condp = type
                        :query :get
                        :command :post)]]
     [path {method {:interceptors [interceptor]}}])
   (if dev-mode? ["/dev-tools" ::dev-tools])])
```

```clj
(cqrs-routes
  [[:query   'get-user]
   [:command 'add-user]
   [:command 'add-order]]
  false)
; ["/api" {:interceptors [::api ::db]}
;  (["/get-user" {:get {:interceptors [get-user]}}]
;   ["/add-user" {:post {:interceptors [add-user]}}]
;   ["/add-order" {:post {:interceptors [add-order]}}])
;  nil]
```


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

## Path-based routing

Path-based routing is done using the `reitit.core/match-by-path` function. It takes the router and path as arguments and returns one of the following:

* `nil`, no match
* `PartialMatch`, path matched, missing path-parameters (only in reverse-routing)
* `Match`, exact match

```clj
(r/match-by-path router "/hello")
; nil
```

```clj
(r/match-by-path router "/api/user/1")
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :result nil
;        :params {:id "1"}}
```

## Name-based routing

All routes which `:name` route data defined, can be matched by name.

Listing all route names:

```clj
(r/route-names router)
; [:user/ping :user/user]
```

Matching by name:

```clj
(r/match-by-name router ::user)
; #PartialMatch{:template "/api/user/:id",
;               :meta {:name :user/user},
;               :result nil,
;               :params nil,
;               :required #{:id}}

(r/partial-match? (r/match-by-name router ::user))
; true
```

We only got a partial match as we didn't provide the needed path-parameters. Let's provide the them too:

```clj
(r/match-by-name router ::user {:id "1"})
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :result nil
;        :params {:id "1"}}
```

There is also a exception throwing version:

```clj
(r/match-by-name! router ::user)
; ExceptionInfo missing path-params for route /api/user/:id: #{:id}
```

# Route data

Routes can have arbitrary meta-data, interpreted by the router (via it's `:compile` hook) or the application itself. For nested routes, route data is accumulated recursively using [meta-merge](https://github.com/weavejester/meta-merge). By default, it appends collections, but it can be overridden to do `:prepend`, `:replace` or `:displace`.

An example router with nested data:

```clj
(def router
  (r/router
    ["/api" {:interceptors [::api]}
     ["/ping" ::ping]
     ["/admin" {:roles #{:admin}}
      ["/users" ::users]
      ["/db" {:interceptors [::db]
              :roles ^:replace #{:db-admin}}
       ["/:db" {:parameters {:db String}}
        ["/drop" ::drop-db]
        ["/stats" ::db-stats]]]]]))
```

Resolved route tree:

```clj
(reitit/routes router)
; [["/api/ping" {:interceptors [::api]
;                :name ::ping}]
;  ["/api/admin/users" {:interceptors [::api]
;                       :roles #{:admin}
;                       :name ::users}]
;  ["/api/admin/db/:db/drop" {:interceptors [::api ::db]
;                             :roles #{:db-admin}
;                             :parameters {:db String}
;                             :name ::drop-db}]
;  ["/api/admin/db/:db/stats" {:interceptors [::api ::db]
;                              :roles #{:db-admin}
;                              :parameters {:db String}
;                              :name ::db-stats}]]
```

Route data is returned with `Match` and the application can act based on it.

```clj
(r/match-by-path router "/api/admin/db/users/drop")
; #Match{:template "/api/admin/db/:db/drop"
;        :meta {:interceptors [::api ::db]
;               :roles #{:db-admin}
;                :parameters {:db String}
;        :name ::drop-db}
;        :result nil
;        :params {:db "users"}
;        :path "/api/admin/db/users/drop"}
```

# Different Routers

Reitit ships with several different implementations for the `Router` protocol, originally based on the awesome [Pedestal](https://github.com/pedestal/pedestal/tree/master/route) implementation. `router` selects the most suitable implementation by inspecting the expanded routes. The implementation can be set manually using `:router` ROUTER OPTION.

| router                | description |
| ----------------------|-------------|
| `:linear-router`      | Matches the routes one-by-one starting from the top until a match is found. Works with any kind of routes.
| `:lookup-router`      | Fastest router, uses hash-lookup to resolve the route. Valid if no paths have path or catch-all parameters.
| `:mixed-router`       | Creates internally a `:linear-router` and a `:lookup-router` and used them to effectively get best-of-both-worlds. Valid if there are no CONFLICTING ROUTES.
| `:prefix-tree-router` | [TODO](https://github.com/julienschmidt/httprouter#how-does-it-work)

The router name can be asked from the router

```clj
(r/router-name router)
; :mixed-router
```
