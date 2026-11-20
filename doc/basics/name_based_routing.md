# Name-based (reverse) Routing

All routes which have `:name` route data defined can also be matched by name.

Given a router:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    ["/api"
     ["/ping" :user/ping]
     ["/user/:id" :user/user]]))
```

Listing all route names:

```clj
(r/route-names router)
;; => [:user/ping :user/user]
```

No match returns `nil`:

```clj
(r/match-by-name router :user/kikka)
;; => nil
```

Matching a route:

```clj
(r/match-by-name router :user/ping)
;; => {:template "/api/ping"
;;     :data {:name :user/ping}
;;     :result nil
;;     :path-params {}
;;     :path "/api/ping"}
```

If not all path-parameters are set, a `PartialMatch` is returned:

```clj
(r/match-by-name router :user/user)
;; => {:template "/api/user/:id",
;;     :data {:name :user/user},
;;     :result nil,
;;     :path-params nil,
;;     :required #{:id}}

(r/partial-match? (r/match-by-name router :user/user))
;; => true
```

With provided path-parameters:

```clj
(r/match-by-name router :user/user {:id "1"})
;; => {:template "/api/user/:id"
;;     :data {:name :user/user}
;;     :path "/api/user/1"
;;     :result nil
;;     :path-params {:id "1"}}
```

Path-parameters are automatically coerced into strings, with the help of (currently internal) Protocol `reitit.impl/IntoString`. It supports strings, numbers, booleans, keywords and objects:

```clj
(r/match-by-name router :user/user {:id 1})
;; => {:template "/api/user/:id"
;;     :data {:name :user/user}
;;     :path "/api/user/1"
;;     :result nil
;;     :path-params {:id "1"}}
```

In case you want to do something like generate a template path for documentation, you can disable url-encoding:

```clj
(r/match-by-name router :user/user {:id "<id goes here>"} {:url-encode? false})
;; => {:template "/api/user/:id"
;;     :data {:name :user/user}
;;     :path "/api/user/<id goes here>"
;;     :result nil
;;     :path-params {:id "<id goes here>"}}
```

There is also an exception throwing version:

```clj
(r/match-by-name! router :user/user)
;; =thrown-match=> {:type "missing path-params for route /api/user/:id -> #{:id}"}
```

To turn a Match into a path, there is `reitit.core/match->path`:

```clj
(-> router
    (r/match-by-name :user/user {:id 1})
    (r/match->path))
;; => "/api/user/1"
```

It can take an optional map of query-parameters too:

```clj
(-> router
    (r/match-by-name :user/user {:id 1})
    (r/match->path {:iso "möly"}))
;; => "/api/user/1?iso=m%C3%B6ly"
```
