# Route Syntax

Routes are defined as vectors of String path and optional (non-sequential) route argument child routes.

Routes can be wrapped in vectors and lists and `nil` routes are ignored.

Paths can have path-parameters (`:id`) or catch-all-parameters (`*path`). Since version `0.4.0`, parameters can also be wrapped in brackets, enabling use of qualified keywords `{user/id}`, `{*user/path}`. The non-bracket syntax might be deprecated later.

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

```clj
[["/users/{user-id}"]
 ["/files/file-{number}.pdf"]]
```

Route with catch-all parameter:

```clj
["/public/*path"]
```

```clj
["/public/{*path}"]
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

### Encoding

Reitit does not apply any encoding to your paths. If you need that, you must encode them yourself. E.g., `/foo bar` should be `/foo%20bar`.

### Wildcards

Normal path-parameters (`:id`) can start anywhere in the path string, but have to end either to slash `/` (currently hardcoded) or to en end of path string:

```clj
[["/api/:version"]
 ["/files/file-:number"]
 ["/user/:user-id/orders"]]
```

Bracket path-parameters can start and stop anywhere in the path-string, the following character is used as a terminator.

```clj
[["/api/{version}"]
 ["/files/{name}.{extension}"]
 ["/user/{user-id}/orders"]]
```

Having multiple terminators after a bracket path-path parameter with identical path prefix will cause a compile-time error at router creation:

```clj
[["/files/file-{name}.pdf"]            ;; terminator \.
 ["/files/file-{name}-{version}.pdf"]] ;; terminator \-
```

### Slash Free Routing

```clj
[["broker.{customer}.{device}.{*data}"]
 ["events.{target}.{type}"]]
```

### Generating routes

Routes are just data, so it's easy to create them programmatically:

```clj
(defn cqrs-routes [actions]
  ["/api" {:interceptors [::api ::db]}
   (for [[type interceptor] actions
         :let [path (str "/" (name interceptor))
               method (case type
                        :query :get
                        :command :post)]]
     [path {method {:interceptors [interceptor]}}])])
```

```clj
(cqrs-routes
  [[:query   'get-user]
   [:command 'add-user]
   [:command 'add-order]])
; ["/api" {:interceptors [::api ::db]}
;  (["/get-user" {:get {:interceptors [get-user]}}]
;   ["/add-user" {:post {:interceptors [add-user]}}]
;   ["/add-order" {:post {:interceptors [add-order]}}])]
```
