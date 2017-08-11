# reitit [![Build Status](https://travis-ci.org/metosin/reitit.svg?branch=master)](https://travis-ci.org/metosin/reitit) [![Dependencies Status](https://jarkeeper.com/metosin/reitit/status.svg)](https://jarkeeper.com/metosin/reitit)

Snappy data-driven router for Clojure(Script).

* Simple data-driven route syntax
* First-class route meta-data
* Generic, not tied to HTTP
* Extendable
* Fast

## Latest version

[![Clojars Project](http://clojars.org/metosin/reitit/latest-version.svg)](http://clojars.org/metosin/reitit)

## Route Syntax

Routes are defined as vectors, which String path as the first element, then optional meta-data (non-vector) and optional child routes. Routes can be wrapped in vectors.

Simple route:

```clj
["/ping"]
```

Two routes:

```clj
[["/ping]
 ["/pong]]
```

Routes with meta-data:

```clj
[["/ping ::ping]
 ["/pong {:name ::pong}]]
```

Nested routes with meta-data:

```clj
["/api"
 ["/admin" {:middleware [::admin]}
  ["/user" ::user]
  ["/db" ::db]
 ["/ping" ::ping]]
```

Previous example flattened:

```clj
[["/api/admin/user" {:middleware [::admin], :name ::user}
 ["/api/admin/db" {:middleware [::admin], :name ::db}
 ["/api/ping" ::ping]]
```

## Routers

For actual routing, we need to create a `Router`. Reitit ships with 2 different router implementations: `LinearRouter` and `LookupRouter`, both based on the awesome [Pedestal](https://github.com/pedestal/pedestal/tree/master/route) implementation.

`Router` is created with `reitit.core/router`, which takes routes and optionally an options map as arguments. The route-tree gets expanded, optionally coerced and compiled to support both fast path- and name-based lookups.

Create a router:

```clj
(require '[reitit.core :as reitit])

(def router
  (reitit/router
    [["/api"
      ["/ping" ::ping]
      ["/user/:id ::user]]))

(class router)
; reitit.core.LinearRouter
```

Get the expanded routes:

```clj
(reitit/routes router)
; [["/api/ping" {:name :user/ping}]
;  ["/api/user/:id" {:name :user/user}]]
```

Path-based routing:

```clj
(reitit/match-by-path router "/hello")
; nil

(reitit/match-by-path router "/api/user/1")
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :params {:id "1"}}
```

Name-based (reverse) routing:

```clj
(reitit/match-by-name router ::user)
; ExceptionInfo missing path-params for route '/api/user/:id': #{:id}
```

Oh, that didn't work, retry:

```clj
(reitit/match-by-name router ::user {:id "1"})
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :params {:id "1"}}
```

## Route meta-data

Routes can have arbitrary meta-data. For nested routes, the meta-data is accumulated from root towards leafs using [meta-merge](https://github.com/weavejester/meta-merge).

A router based on nested route tree:

```clj
(def ring-router
  (reitit/router
    ["/api" {:middleware [:api-mw]}
     ["/ping" ::ping]
     ["/public/*path" ::resources]
     ["/user/:id" {:name ::get-user
                   :parameters {:id String}}
      ["/orders" ::user-orders]]
     ["/admin" {:middleware [:admin-mw]
                :roles #{:admin}}
      ["/root" {:name ::root
                :roles ^:replace #{:root}}]
      ["/db" {:name ::db
              :middleware [:db-mw]}]]]))
```

Expanded and merged route tree:

```clj
(reitit/routes ring-router)
; [["/api/ping" {:name :user/ping
;                :middleware [:api-mw]}]
;  ["/api/public/*path" {:name :user/resources
;                        :middleware [:api-mw]}]
;  ["/api/user/:id/orders" {:name :user/user-orders
;                           :middleware [:api-mw]
;                           :parameters {:id String}}]
;  ["/api/admin/root" {:name :user/root
;                      :middleware [:api-mw :admin-mw]
;                      :roles #{:root}}]
;  ["/api/admin/db" {:name :user/db
;                    :middleware [:api-mw :admin-mw :db-mw]
;                    :roles #{:admin}}]]
```

Path-based routing:

```clj
(reitit/match-by-path ring-router "/api/admin/root")
; #Match{:template "/api/admin/root"
;        :meta {:name :user/root
;               :middleware [:api-mw :admin-mw]
;               :roles #{:root}}
;        :path "/api/admin/root"
;        :params {}}
```

Route meta-data is just data and the actual interpretation is left to the application. `Router` will get more options in the future to do things like [`clojure.spec`](https://clojure.org/about/spec) validation and custom route compilation (into into [Ring](https://github.com/ring-clojure/ring)-handlers or [Pedestal](pedestal.io)-style interceptors). See [Open issues](https://github.com/metosin/reitit/issues/).

## Special thanks

To all Clojure(Script) routing libs out there, expecially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [Compojure](https://github.com/weavejester/compojure) and
[Pedestal](https://github.com/pedestal/pedestal/tree/master/route).

## License

Copyright © 2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
