# Route Data

Route data is the heart of this library. Routes can have any data attachted to them. Data is interpeted either by the client application or the `Router` via it's `:coerce` and `:compile` hooks. Together with `clojure.spec` -validation this enables co-existence of both [adaptive and principled](https://youtu.be/x9pxbnFC4aQ?t=1907) components.

Routes can have a non-sequential route argument that is expanded into route data map when a router is created.

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

(r/match-by-path router "/ping")
; #Match{:template "/ping"
;        :data {:name :user/ping}
;        :result nil
;        :path-params {}
;        :path "/ping"}

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

By default, `reitit/Expand` protocol is used to expand the route arguments. It expands keywords into `:name` and functions into `:handler` key in the route data map. It's easy to add custom expanders and one can chenge the whole expand implementation via [router options](../advanced/configuring_routers.md).

```clj
(def router
  (r/router
    [["/ping" ::ping]
     ["/pong" identity]
     ["/users" {:get {:roles #{:admin}
                      :handler identity}}]]))

(r/routes router)
; [["/ping" {:name :user/ping}]
;  ["/pong" {:handler identity]}
;  ["/users" {:get {:roles #{:admin}
;                   :handler identity}}]]

(r/match-by-path router "/ping")
; #Match{:template "/ping"
;        :data {:name :user/ping}
;        :result nil
;        :path-params {}
;        :path "/ping"}
```
