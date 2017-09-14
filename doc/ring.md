# Ring support

[Ring](https://github.com/ring-clojure/ring)-router adds support for ring concepts like [handlers](https://github.com/ring-clojure/ring/wiki/Concepts#handlers), [middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware) and routing based on `:request-method`. Ring-router is created with `reitit.ring/router` function. It runs a custom route compiler, creating a optimized stucture for handling route matches, with compiled middleware chain & handlers for all request methods. It also ensures that all routes have a `:handler` defined.

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

Applying the handler:

```clj
(app {:request-method :get, :uri "/favicon.ico"})
; nil

(app {:request-method :get, :uri "/ping"})
; {:status 200, :body "ok"}
```

The expanded routes:

```clj
(-> app (ring/get-router) (reitit/routes))
; [["/ping"
;   {:handler #object[...]}
;   #Methods{:any #Endpoint{:meta {:handler #object[...]},
;                           :handler #object[...],
;                           :middleware []}}]]
```

Note that the compiled resuts as third element in the route vector.

### Request-method based routing

Handler are also looked under request-method keys: `:get`, `:head`, `:patch`, `:delete`, `:options`, `:post` or `:put`. Top-level handler is used if request-method based handler is not found.

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

Middleware can be added with a `:middleware` key, with a vector value of the following:

1. ring middleware function `handler -> request -> response`
2. vector of middleware function `handler ?args -> request -> response` and optinally it's args.

A middleware and a handler:

```clj
(defn wrap [handler id]
  (fn [request]
    (handler (update request ::acc (fnil conj []) id))))

(defn handler [{:keys [::acc]}]
  {:status 200, :body (conj acc :handler)})
```

App with nested middleware:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [#(wrap % :api)]}
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

Reitit supports first-class data-driven middleware via `reitit.middleware/Middleware` records, created with `reitit.middleware/create` function. The following keys have special purpose:

| key        | description |
| -----------|-------------|
| `:name`    | Name of the middleware as qualified keyword (optional,recommended for libs)
| `:wrap`    | The actual middleware function of `handler args? => request => response`
| `:gen`     | Middleware compile function, see [compiling middleware](#compiling-middleware).

When routes are compiled, all middleware are expanded (and optionally compiled) into `Middleware` and stored in compilation results for later use (api-docs etc). For actual request processing, they are unwrapped into normal middleware functions producing zero runtime performance penalty. Middleware expansion is backed by `reitit.middleware/IntoMiddleware` protocol, enabling plain clojure(script) maps to be used.

A Record:

```clj
(require '[reitit.middleware :as middleware])

(def wrap2
  (middleware/create
    {:name ::wrap2
     :description "a nice little mw, takes 1 arg."
     :wrap wrap}))
```

As plain map:

```clj
;; plain map
(def wrap3
  {:name ::wrap3
   :description "a nice little mw, :api as arg"
   :wrap (fn [handler]
           (wrap handler :api))})
```

### Async Ring

All built-in middleware provide both 2 and 3-arity and are compiled for both Clojure & ClojureScript, so they work with [Async Ring](https://www.booleanknot.com/blog/2016/07/15/asynchronous-ring.html) and [Node.js](https://nodejs.org) too.

### Meta-data based extensions

`ring-handler` injects the `Match` into a request and it can be extracted at runtime with `reitit.ring/get-match`. This can be used to build dynamic extensions to the system.

Example middleware to guard routes based on user roles:

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
