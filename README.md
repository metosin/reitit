# reitit [![Build Status](https://travis-ci.org/metosin/reitit.svg?branch=master)](https://travis-ci.org/metosin/reitit) [![Dependencies Status](https://jarkeeper.com/metosin/reitit/status.svg)](https://jarkeeper.com/metosin/reitit)

A friendly data-driven router for Clojure(Script).

* Simple data-driven route syntax
* First-class route meta-data
* Generic, not tied to HTTP
* [Route conflict resolution](#route-conflicts)
* [Pluggable coercion](#parameter-coercion) ([clojure.spec](https://clojure.org/about/spec))
* both Middleware & Interceptors
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

For routing, a `Router` is needed. Reitit ships with several different router implementations: `:linear-router`, `:lookup-router` and `:mixed-router`, based on the awesome [Pedestal](https://github.com/pedestal/pedestal/tree/master/route) implementation.

`Router` is created with `reitit.core/router`, which takes routes and optional options map as arguments. The route tree gets expanded, optionally coerced and compiled. Actual `Router` implementation is selected automatically but can be defined with a `:router` option. `Router` support both path- and name-based lookups.

Creating a router:

```clj
(require '[reitit.core :as reitit])

(def router
  (reitit/router
    [["/api"
      ["/ping" ::ping]
      ["/user/:id" ::user]]]))
```

`:mixed-router` is created (both static & wild routes are found):

```clj
(reitit/router-name router)
; :mixed-router
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

### Path-based routing

```clj
(reitit/match-by-path router "/hello")
; nil

(reitit/match-by-path router "/api/user/1")
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :result nil
;        :params {:id "1"}}
```

### Name-based (reverse) routing

```clj
(reitit/match-by-name router ::user)
; #PartialMatch{:template "/api/user/:id",
;               :meta {:name :user/user},
;               :result nil,
;               :params nil,
;               :required #{:id}}

(reitit/partial-match? (reitit/match-by-name router ::user))
; true
```

Only a partial match. Let's provide the path-parameters:

```clj
(reitit/match-by-name router ::user {:id "1"})
; #Match{:template "/api/user/:id"
;        :meta {:name :user/user}
;        :path "/api/user/1"
;        :result nil
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
  (reitit/router
    ["/api" {:interceptors [::api]}
     ["/ping" ::ping]
     ["/admin" {:roles #{:admin}}
      ["/users" ::users]
      ["/db" {:interceptors [::db], :roles ^:replace #{:db-admin}}
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

Routers also support meta-data compilation enabling things like fast [Ring](https://github.com/ring-clojure/ring) or [Pedestal](http://pedestal.io/) -style handlers. Compilation results are found under `:result` in the match. See [configuring routers](#configuring-routers) for details.

## Route conflicts

Route trees should not have multiple routes that match to a single (request) path. `router` checks the route tree at creation for conflicts and calls a registered `:conflicts` option callback with the found conflicts. Default implementation throws `ex-info` with a descriptive message.

```clj
(reitit/router
  [["/ping"]
   ["/:user-id/orders"]
   ["/bulk/:bulk-id"]
   ["/public/*path"]
   ["/:version/status"]])
; CompilerException clojure.lang.ExceptionInfo: router contains conflicting routes:
;
;    /:user-id/orders
; -> /public/*path
; -> /bulk/:bulk-id
;
;    /bulk/:bulk-id
; -> /:version/status
;
;    /public/*path
; -> /:version/status
;
```

## Ring

[Ring](https://github.com/ring-clojure/ring)-router adds support for ring [handlers](https://github.com/ring-clojure/ring/wiki/Concepts#handlers), [middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware) and routing based on `:request-method`. Ring-router is created with `reitit.ring/router` function. It validates that all paths have a `:handler` defined and expands `:middleware` to create accumulated handlers for all request-methods. `reitit.ring/ring-handler` creates an actual ring handler out of a ring-router.

Simple Ring app:

```clj
(require '[reitit.ring :as ring])

(defn handler [_]
  {:status 200, :body "ok"})

(def app
  (ring/ring-handler
    (ring/router
      ["/ping" handler])))
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

### Request-method based routing

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

### Middleware

`:middleware` should be a vector of either of the following (expanded via the `reitit.middleware/ExpandMiddleware`:

1. a ring middleware function of `handler -> request -> response`
2. a vector of middleware function (`handler args -> request -> response`) and it's args - actial middleware is created by applying function with handler and args

Let's define some middleware and a handler:

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

```clj
(app {:request-method :delete, :uri "/api/admin/db"})
; {:status 200, :body [:api :admin :db :delete :handler]}
```

### Middleware Records

Besides just being opaque functions, middleware can be presented as first-class data entries, `reitit.middleware/Middleware` records. They are created with `reitit.middleware/create` function and must have a `:name` and either `:wrap` or `:gen` key with the actual middleware function or a [middleware generator function](#compiling-middleware).

When routes are compiled, middleware records are unwrapped into normal middleware functions producing no runtime performance penalty. Thanks to  the `ExpandMiddleware` protocol, plain clojure(script) maps can also be used - they get expanded into middleware records.

The previous middleware re-written as records:

```clj
(require '[reitit.middleware :as middleware])

(def wrap2
  (middleware/create
    {:name ::wrap
     :description "a nice little mw, takes 1 arg."
     :wrap wrap}))

(def wrap2-api
  {:name ::wrap-api
   :description "a nice little mw, :api as arg"
   :wrap (fn [handler]
           (wrap handler :api))})
```

Or as maps:

```clj
(require '[reitit.middleware :as middleware])

(def wrap3
  {:name ::wrap
   :description "a nice little mw, takes 1 arg."
   :wrap wrap})

(def wrap3-api
  {:name ::wrap-api
   :description "a nice little mw, :api as arg"
   :wrap (fn [handler]
           (wrap handler :api))})
```




### Async Ring

All built-in middleware provide both the 2 and 3-arity, so they work with [Async Ring](https://www.booleanknot.com/blog/2016/07/15/asynchronous-ring.html) too.

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

## Parameter coercion

Reitit ships with pluggable parameter coercion via `reitit.coercion.protocol/Coercion` protocol. `reitit.coercion.spec/SpecCoercion` provides implements it for [clojure.spec](https://clojure.org/about/spec) & [data-specs](https://github.com/metosin/spec-tools#data-specs).

**NOTE**: to use the spec-coercion, one needs to add the following dependencies manually to the project:

```clj
[org.clojure/clojure "1.9.0-alpha19"]
[org.clojure/spec.alpha "0.1.123"]
[metosin/spec-tools "0.3.3"]
```

### Ring request and response coercion

To use `Coercion` with Ring, one needs to do the following:

1. Define parameters and responses as data into route meta-data, in format adopted from [ring-swagger](https://github.com/metosin/ring-swagger#more-complete-example):
  * `:parameters` map, with submaps for different parameters: `:query`, `:body`, `:form`, `:header` and `:path`. Parameters are defined in the format understood by the `Coercion`.
  * `:responses` map, with response status codes as keys (or `:default` for "everything else") with maps with `:schema` and optionally `:description` as values.
2. Define a `Coercion` to route meta-data under `:coercion`
3. Mount request & response coercion middleware to the routes.

If the request coercion succeeds, the coerced parameters are injected into request under `:parameters`.

If either request or response coercion fails, an descriptive error is thrown.

#### Example with data-specs

```clj
(require '[reitit.ring :as ring])
(require '[reitit.coercion :as coercion])
(require '[reitit.coercion.spec :as spec])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/ping" {:parameters {:body {:x int?, :y int?}}
                 :responses {200 {:schema {:total pos-int?}}}
                 :get {:handler (fn [{{{:keys [x y]} :body} :parameters}]
                                  {:status 200
                                   :body {:total (+ x y)}})}}]]
      {:meta {:middleware [coercion/gen-wrap-coerce-parameters
                           coercion/gen-wrap-coerce-response]
              :coercion spec/coercion}})))
```


```clj
(app
  {:request-method :get
   :uri "/api/ping"
   :body-params {:x 1, :y 2}})
; {:status 200, :body {:total 3}}
```

#### Example with specs

```clj
(require '[reitit.ring :as ring])
(require '[reitit.coercion :as coercion])
(require '[reitit.coercion.spec :as spec])
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.core :as st])

(s/def ::x (st/spec int?))
(s/def ::y (st/spec int?))
(s/def ::total int?)
(s/def ::request (s/keys :req-un [::x ::y]))
(s/def ::response (s/keys :req-un [::total]))

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/ping" {:parameters {:body ::request}
                 :responses {200 {:schema ::response}}
                 :get {:handler (fn [{{{:keys [x y]} :body} :parameters}]
                                  {:status 200
                                   :body {:total (+ x y)}})}}]]
      {:meta {:middleware [coercion/gen-wrap-coerce-parameters
                           coercion/gen-wrap-coerce-response]
              :coercion spec/coercion}})))
```

```clj
(app
  {:request-method :get
   :uri "/api/ping"
   :body-params {:x 1, :y 2}})
; {:status 200, :body {:total 3}}
```

## Compiling Middleware

The [meta-data extensions](#meta-data-based-extensions) are a easy way to extend the system. Routes meta-data can be trasnformed into any shape (records, functions etc.) in route compilation, enabling easy access at request-time.

Still, we can do better. As we know the exact route interceptor/middleware is linked to, we can pass the (compiled) route information into the interceptor/middleware at creation-time. It can extract and transform relevant data just for it and pass it into the actual request-handler via a closure. We can do all the static local computations forehand, yielding faster runtime processing.

To do this we use [middleware records](#middleware-records) `:gen` hook instead of the normal `:wrap`. `:gen` expects a function of `route-meta router-opts => wrap`. Instead of returning the actual middleware function, the middleware record can also decide no to mount itsef byt returning `nil`. Why mount `wrap-enforce-roles` for a route if there are no roles required for it?

To demonstrate the two approaches, below are response coercion middleware written as normal ring middleware function and as middleware record with `:gen`. The actual codes are from `reitit.coercion`:

### Naive

* Extracts the compiled route information on every request.

```clj
(defn wrap-coerce-response
  "Pluggable response coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :responeses from route meta, otherwise does not mount."
  [handler]
  (fn
    ([request]
     (let [response (handler request)
           method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :meta :responses)
           coercion (-> match :meta :coercion)
           opts (-> match :meta :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)
               coerced (coerce-response coercers request response)]
           (coerce-response coercers request (handler request)))
         (handler request))))
    ([request respond raise]
     (let [response (handler request)
           method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :meta :responses)
           coercion (-> match :meta :coercion)
           opts (-> match :meta :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)
               coerced (coerce-response coercers request response)]
           (handler request #(respond (coerce-response coercers request %))))
         (handler request respond raise))))))
```

### Compiled

* Route information is provided via a closure
* Pre-compiled coercers
* Mounts only if `:coercion` and `:responses` are defined for the route

```clj
(def gen-wrap-coerce-response
  "Generator for pluggable response coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :responses from route meta, otherwise does not mount."
  (middleware/create
    {:name ::coerce-response
     :gen (fn [{:keys [responses coercion opts]} _]
            (if (and coercion responses)
              (let [coercers (response-coercers coercion responses opts)]
                (fn [handler]
                  (fn
                    ([request]
                     (coerce-response coercers request (handler request)))
                    ([request respond raise]
                     (handler request #(respond (coerce-response coercers request %)) raise)))))))}))
```

The `:gen` -version is both much easier to understand but also 2-4x faster on basic perf tests.

## Merging route-trees

*TODO*

## Validating meta-data

*TODO*

## Swagger & Openapi

*TODO*

## Interceptors

*TODO*

## Configuring Routers

Routers can be configured via options. Options allow things like [`clojure.spec`](https://clojure.org/about/spec) validation for meta-data and fast, compiled handlers. The following options are available for the `reitit.core/router`:

  | key          | description |
  | -------------|-------------|
  | `:path`      | Base-path for routes
  | `:routes`    | Initial resolved routes (default `[]`)
  | `:meta`      | Initial expanded route-meta vector (default `[]`)
  | `:expand`    | Function of `arg opts => meta` to expand route arg to route meta-data (default `reitit.core/expand`)
  | `:coerce`    | Function of `route opts => route` to coerce resolved route, can throw or return `nil`
  | `:compile`   | Function of `route opts => result` to compile a route handler
  | `:conflicts` | Function of `{route #{route}} => side-effect` to handle conflicting routes (default `reitit.core/throw-on-conflicts!`)
  | `:router`    | Function of `routes opts => router` to override the actual router implementation

## Special thanks

To all Clojure(Script) routing libs out there, expecially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [Compojure](https://github.com/weavejester/compojure) and
[Pedestal](https://github.com/pedestal/pedestal/tree/master/route).

## License

Copyright © 2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
