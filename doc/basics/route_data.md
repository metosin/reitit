# Route Data

Route data is the key feature of reitit. Routes can have any map-like data attached to them, to be interpreted by the client application, `Router` or routing components like `Middleware` or `Interceptors`.

```clj
[["/ping" {:name ::ping}]
 ["/pong" {:handler identity}]
 ["/users" {:get {:roles #{:admin}
                  :handler identity}}]]
```

Besides map-like data, raw routes can have any non-sequential route argument after the path. This argument is expanded by `Router` (via `:expand` option) into route data at router creation time. 

By default, Keywords are expanded into `:name` and functions into `:handler` keys.

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/ping" ::ping]
     ["/pong" identity]
     ["/users" {:get {:roles #{:admin}
                      :handler identity}}]]))
```

## Using Route Data

Expanded route data can be retrieved from a router with `routes` and is returned with `match-by-path` and `match-by-name` in case of a route match.

```clj
(r/routes router)
; [["/ping" {:name ::ping}]
;  ["/pong" {:handler identity]}
;  ["/users" {:get {:roles #{:admin}
;                   :handler identity}}]]
```

```clj
(r/match-by-path router "/ping")
; #Match{:template "/ping"
;        :data {:name :user/ping}
;        :result nil
;        :path-params {}
;        :path "/ping"}
```

```clj
(r/match-by-name router ::ping)
; #Match{:template "/ping"
;        :data {:name :user/ping}
;        :result nil
;        :path-params {}
;        :path "/ping"}
```

## Nested Route Data

For nested route trees, route data is accumulated recursively from root towards leafs using [meta-merge](https://github.com/weavejester/meta-merge). Default behavior for collections is `:append`, but this can be overridden to `:prepend`, `:replace` or `:displace` using the target meta-data.

An example router with nested data:

```clj
(def router
  (r/router
    ["/api" {:interceptors [::api]}
     ["/ping" ::ping]
     ["/admin" {:roles #{:admin}}
      ["/users" ::users]
      ["/db" {:interceptors [::db]
              :roles ^:replace #{:db-admin}}]]]))
```

Resolved route tree:

```clj
(r/routes router)
; [["/api/ping" {:interceptors [::api]
;                :name :user/ping}]
;  ["/api/admin/users" {:interceptors [::api]
;                       :roles #{:admin}
;                       :name ::users} nil]
;  ["/api/admin/db" {:interceptors [::api ::db]
;                    :roles #{:db-admin}}]]
```

## Route Data Fragments

Just like [fragments in React.js](https://reactjs.org/docs/fragments.html), we can create routing tree fragments by using empty path `""`. This allows us to add route data without accumulating to path.

Given a route tree:

```clj
[["/swagger.json" ::swagger]
 ["/api-docs" ::api-docs]
 ["/api/ping" ::ping]
 ["/api/pong" ::pong]]
```

Adding `:no-doc` route data to exclude the first routes from generated [Swagger documentation](../ring/swagger.md):

```clj
[["" {:no-doc true}
  ["/swagger.json" ::swagger]
  ["/api-docs" ::api-docs]]
 ["/api/ping" ::ping]
 ["/api/pong" ::pong]]
```

Accumulated route data:

```clj
(def router
  (r/router
    [["" {:no-doc true}
      ["/swagger.json" ::swagger]
      ["/api-docs" ::api-docs]]
     ["/api/ping" ::ping]
     ["/api/pong" ::pong]]))
     
(r/routes router)
; [["/swagger.json" {:no-doc true, :name ::swagger}]
;  ["/api-docs" {:no-doc true, :name ::api-docs}]
;  ["/api/ping" {:name ::ping}]
;  ["/api/pong" {:name ::pong}]]
```

## Top-level Route Data

Route data can be introduced also via `Router` option `:data`:

```clj
(def router
  (r/router
    ["/api"
     {:middleware [::api]}
     ["/ping" ::ping]
     ["/pong" ::pong]]
    {:data {:middleware [::session]}}))
```

Expanded routes:

```clj
[["/api/ping" {:middleware [::session ::api], :name ::ping}]
 ["/api/pong" {:middleware [::session ::api], :name ::pong}]]
```


## Customizing Expansion

By default, router `:expand` option has value `r/expand` function, backed by a `r/Expand` protocol. Expansion can be customized either by swapping the `:expand` implementation or by extending the Protocol. `r/Expand` implementations can be recursive.

Naive example to add direct support for `java.io.File` route argument:

```clj
(extend-type java.io.File
  r/Expand
  (expand [file options]
    (r/expand
      #(slurp file)
      options)))

(r/router
  ["/" (java.io.File. "index.html")])
```

Page [shared routes](../advanced/shared_routes.md#using-custom-expander) has an example of an custom `:expand` implementation.

## Route data validation

See [Route data validation](route_data_validation.md).
