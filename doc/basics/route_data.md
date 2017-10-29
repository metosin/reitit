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
