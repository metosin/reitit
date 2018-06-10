# Route Data

Route data is the core feature of reitit. Routes can have any map-like data attached to them. This data is interpreted either by the client application or the `Router` via its `:coerce` and `:compile` hooks. Route data format can be defined and validated with `clojure.spec` enabling a architecture of both [adaptive and principled](https://youtu.be/x9pxbnFC4aQ?t=1907) components.

Raw routes can have a non-sequential route argument that is expanded (via router `:expand` hook) into route data at router creation time. By default, Keywords are expanded into `:name` and functions into `:handler` keys.

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/ping" ::ping]
     ["/pong" identity]
     ["/users" {:get {:roles #{:admin}
                      :handler identity}}]]))
```

The expanded route data can be retrieved from a router with `routes` and is returned with `match-by-path` and `match-by-name` in case of a route match.

```clj
(r/routes router)
; [["/ping" {:name :user/ping}]
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

## Nested route data

For nested route trees, route data is accumulated recursively from root towards leafs using [meta-merge](https://github.com/weavejester/meta-merge). Default behavior for colections is `:append`, but this can be overridden to `:prepend`, `:replace` or `:displace` using the target meta-data.

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


## Expansion

By default, router `:expand` hook maps to `reitit.core/expand` function, backed by a `reitit.core/Expand` protocol. One can provide either a totally different function or add new implementations to that protocol. Expand implementations can be recursive.

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

See [router options](../advanced/configuring_routers.md) for all available options.
