# Route Syntax

Routes are defined as vectors of String path and optional (non-sequential) route argument child routes.

Routes can be wrapped in vectors and lists and `nil` routes are ignored.

Paths can have path-parameters (`:id`) or catch-all-parameters (`*path`).

### Examples

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

### Generating routes
As routes are just data, it's easy to create them programmatically:

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
