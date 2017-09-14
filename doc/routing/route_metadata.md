# Route meta-data

Routes can have arbitrary meta-data. For nested routes, the meta-data is accumulated from root towards leafs using [meta-merge](https://github.com/weavejester/meta-merge).

A router based on nested route tree:

```clj
(def router
  (reitit/router
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

Path-based routing:

```clj
(reitit/match-by-path router "/api/admin/users")
; #Match{:template "/api/admin/users"
;        :meta {:interceptors [::api]
;               :roles #{:admin}
;               :name ::users}
;        :result nil
;        :params {}
;        :path "/api/admin/users"}
```

On match, route meta-data is returned and can interpreted by the application.

Routers also support meta-data compilation enabling things like fast [Ring](https://github.com/ring-clojure/ring) or [Pedestal](http://pedestal.io/) -style handlers. Compilation results are found under `:result` in the match. See [configuring routers](../configuring_routers.md) for details.
