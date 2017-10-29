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
