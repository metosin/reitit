# reitit [![Build Status](https://travis-ci.org/metosin/reitit.svg?branch=master)](https://travis-ci.org/metosin/reitit) [![Dependencies Status](https://jarkeeper.com/metosin/reitit/status.svg)](https://jarkeeper.com/metosin/reitit)

A friendly data-driven router for Clojure(Script).

* Simple data-driven route syntax
* First-class route meta-data
* Generic, not tied to HTTP
* Extendable
* Fast

Ships with example router for [Ring](#ring). See [Issues](https://github.com/metosin/reitit/issues) for roadmap.

## Latest version

[![Clojars Project](http://clojars.org/metosin/reitit/latest-version.svg)](http://clojars.org/metosin/reitit)

## Route Syntax

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

## Routing

For routing, a `Router` is needed. Reitit ships with 2 different router implementations: `:linear-router` and `:lookup-router`, both based on the awesome [Pedestal](https://github.com/pedestal/pedestal/tree/master/route) implementation.

`Router` is created with `reitit.core/router`, which takes routes and optional options map as arguments. The route-tree gets expanded, optionally coerced and compiled. `Router` support both fast path- and name-based lookups.

Creating a router:

```clj
(require '[reitit.core :as reitit])

(def router
  (reitit/router
    [["/api"
      ["/ping" ::ping]
      ["/user/:id" ::user]]]))
```

`:linear-router` is created (as there are wildcard):

```clj
(reitit/router-type router)
; :linear-router
```

The expanded routes:

```clj
(reitit/routes router)
; [["/api/ping" {:name :user/ping}]
;  ["/api/user/:id" {:name :user/user}]]
```

Route names:

```clj
(reitit/route-names router)
; [:user/ping :user/user]
```

Path-based routing:

```clj
(reitit/match-by-path router "/hello")
; nil

(reitit/match-by-path router "/api/user/1")
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :handler nil
;        :params {:id "1"}}
```

Name-based (reverse) routing:

```clj
(reitit/match-by-name router ::user)
; #PartialMatch{:template "/api/user/:id",
;               :meta {:name :user/user},
;               :handler nil,
;               :params nil,
;               :required #{:id}}

(reitit/partial-match? (reitit/match-by-name router ::user))
; true
```

Only a partial match. Let's provide path-parameters:

```clj
(reitit/match-by-name router ::user {:id "1"})
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :handler nil
;        :params {:id "1"}}
```

There is also a exception throwing version:

```
(reitit/match-by-name! router ::user)
; ExceptionInfo missing path-params for route /api/user/:id: #{:id}
```

## Route meta-data

Routes can have arbitrary meta-data. For nested routes, the meta-data is accumulated from root towards leafs using [meta-merge](https://github.com/weavejester/meta-merge).

A router based on nested route tree:

```clj
(def router
  (reitit/router
    ["/api" {:interceptors [::api]}
     ["/ping" ::ping]
     ["/public/*path" ::resources]
     ["/user/:id" {:name ::get-user
                   :parameters {:id String}}
      ["/orders" ::user-orders]]
     ["/admin" {:interceptors [::admin]
                :roles #{:admin}}
      ["/root" {:name ::root
                :roles ^:replace #{:root}}]
      ["/db" {:name ::db
              :interceptors [::db]}]]]))
```

Resolved route tree:

```clj
(reitit/routes router)
; [["/api/ping" {:name :user/ping
;                :interceptors [::api]}]
;  ["/api/public/*path" {:name :user/resources
;                        :interceptors [::api]}]
;  ["/api/user/:id/orders" {:name :user/user-orders
;                           :interceptors [::api]
;                           :parameters {:id String}}]
;  ["/api/admin/root" {:name :user/root
;                      :interceptors [::api ::admin]
;                      :roles #{:root}}]
;  ["/api/admin/db" {:name :user/db
;                    :interceptors [::api ::admin ::db]
;                    :roles #{:admin}}]]
```

Path-based routing:

```clj
(reitit/match-by-path router "/api/admin/root")
; #Match{:template "/api/admin/root"
;        :meta {:name :user/root
;               :interceptors [::api ::admin]
;               :roles #{:root}}
;        :path "/api/admin/root"
;        :handler nil
;        :params {}}
```

On match, route meta-data is returned and can interpreted by the  application.

Routers also support meta-data compilation enabling things like fast [Ring](https://github.com/ring-clojure/ring) or [Pedestal](http://pedestal.io/) -style handlers. Compilation results are found under `:handler` in the match. See [configuring routers](#configuring-routers) for details.

## Ring

Simple [Ring](https://github.com/ring-clojure/ring)-based routing app:

```clj
(require '[reitit.ring :as ring])

(defn handler [_]
  {:status 200, :body "ok"})

(def app
  (ring/ring-handler
    (ring/router
      ["/ping" handler])))
```

Backed by a `:lookup-router` (as no wildcards found):

```clj
(-> app (ring/get-router) (reitit/router-type))
; :lookup-router
```

The expanded routes:

```clj
(-> app (ring/get-router) (reitit/routes))
; [["/ping" {:handler #object[...]}]]
```

Applying the handler:

```clj
(app {:request-method :get, :uri "/favicon.ico"})
; nil

(app {:request-method :get, :uri "/ping"})
; {:status 200, :body "ok"}
```

Routing based on `:request-method`:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/ping" {:name ::ping
                :get handler
                :post handler}])))

(app {:request-method :get, :uri "/ping"})
; {:status 200, :body "ok"}

(app {:request-method :put, :uri "/ping"})
; nil
```

Reverse routing:

```clj
(-> app
    (ring/get-router)
    (reitit/match-by-name ::ping)
    :path)
; "/ping"
```

Some middleware and a new handler:

```clj
(defn wrap [handler id]
  (fn [request]
    (handler (update request ::acc (fnil conj []) id))))

(defn wrap-api [handler]
  (wrap handler :api))

(defn handler [{:keys [::acc]}]
  {:status 200, :body (conj acc :handler)})
```

App with nested middleware:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [wrap-api]}
       ["/ping" handler]
       ["/admin" {:middleware [[wrap :admin]]}
        ["/db" {:middleware [[wrap :db]]
                :delete {:middleware [#(wrap % :delete)]
                         :handler handler}}]]])))
```

Middleware is applied correctly:

```clj
(app {:request-method :delete, :uri "/api/ping"})
; {:status 200, :body [:api :handler]}
```

Nested middleware works too:

```clj
(app {:request-method :delete, :uri "/api/admin/db"})
; {:status 200, :body [:api :admin :db :delete :handler]}
```

Ring-router supports also 3-arity [Async Ring](https://www.booleanknot.com/blog/2016/07/15/asynchronous-ring.html), so it can be used on [Node.js](https://nodejs.org/en/) too.

### Meta-data based extensions

The routing `Match` is injected into a request and can be extracted with `reitit.ring/get-match`. It can be used to build dynamic extensions to the system.

A middleware to guard routes:

```clj
(require '[clojure.set :as set])

(defn wrap-enforce-roles [handler]
  (fn [{:keys [::roles] :as request}]
    (let [required (some-> request (ring/get-match) :meta ::roles)]
      (if (and (seq required) (not (set/intersection required roles)))
        {:status 403, :body "forbidden"}
        (handler request)))))
```

Mounted to an app via router meta-data (effecting all routes):

```clj
(def handler (constantly {:status 200, :body "ok"}))

(def app
  (ring/ring-handler
    (ring/router
      [["/api"
        ["/ping" handler]
        ["/admin" {::roles #{:admin}}
         ["/ping" handler]]]]
      {:meta {:middleware [wrap-enforce-roles]}})))
```

Anonymous access to public route:

```clj
(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body "ok"}
```

Anonymous access to guarded route:

```clj
(app {:request-method :get, :uri "/api/admin/ping"})
; {:status 403, :body "forbidden"}
```

Authorized access to guarded route:

```clj
(app {:request-method :get, :uri "/api/admin/ping", ::roles #{:admin}})
; {:status 200, :body "ok"}
```

## Merging route-trees

*TODO*

## Validating meta-data

*TODO*

## Schema, Spec, Swagger & Openapi

*TODO*

## Interceptors

*TODO*

## Configuring Routers

Routers can be configured via options. Options allow things like [`clojure.spec`](https://clojure.org/about/spec) validation for meta-data and fast, compiled handlers. The following options are available for the `reitit.core/router`:

  | key        | description |
  | -----------|-------------|
  | `:path`    | Base-path for routes (default `""`)
  | `:routes`  | Initial resolved routes (default `[]`)
  | `:meta`    | Initial expanded route-meta vector (default `[]`)
  | `:expand`  | Function of `arg opts => meta` to expand route arg to route meta-data (default `reitit.core/expand`)
  | `:coerce`  | Function of `route opts => route` to coerce resolved route, can throw or return `nil`
  | `:compile` | Function of `route opts => handler` to compile a route handler

## Special thanks

To all Clojure(Script) routing libs out there, expecially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [Compojure](https://github.com/weavejester/compojure) and
[Pedestal](https://github.com/pedestal/pedestal/tree/master/route).

## License

Copyright © 2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
