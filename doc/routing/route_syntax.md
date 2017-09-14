# Route Syntax

Routes are defined as vectors, which String path, optional (non-vector) route argument and optional child routes. Routes can be wrapped in vectors.

Simple route:

```clj
["/ping"]
```

Two routes:

```clj
[["/ping"]
 ["/pong"]]
```

Routes with meta-data:

```clj
[["/ping" ::ping]
 ["/pong" {:name ::pong}]]
```

Routes with path and catch-all parameters:

```clj
[["/users/:user-id"]
 ["/public/*path"]]
```

Nested routes with meta-data:

```clj
["/api"
 ["/admin" {:middleware [::admin]}
  ["/user" ::user]
  ["/db" ::db]
 ["/ping" ::ping]]
```

Same routes flattened:

```clj
[["/api/admin/user" {:middleware [::admin], :name ::user}
 ["/api/admin/db" {:middleware [::admin], :name ::db}
 ["/api/ping" ::ping]]
```
