# Ring Router

[Ring](https://github.com/ring-clojure/ring) is a Clojure web applications library inspired by Python's WSGI and Ruby's Rack. By abstracting the details of HTTP into a simple, unified API, Ring allows web applications to be constructed of modular components that can be shared among a variety of applications, web servers, and web frameworks.

```clj
[metosin/reitit-ring "0.2.2"]
```

Ring-router adds support for [handlers](https://github.com/ring-clojure/ring/wiki/Concepts#handlers), [middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware) and routing based on `:request-method`. Ring-router is created with `reitit.ring/router` function. It uses a custom route compiler, creating a optimized data structure for handling route matches, with compiled middleware chain & handlers for all request methods. It also ensures that all routes have a `:handler` defined. `reitit.ring/ring-handler` is used to create a Ring handler out of ring-router.

### Example

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
```

```clj
(app {:request-method :get, :uri "/ping"})
; {:status 200, :body "ok"}
```

The expanded routes shows the compilation results:

```clj
(-> app (ring/get-router) (reitit/routes))
; [["/ping"
;   {:handler #object[...]}
;   #Methods{:any #Endpoint{:data {:handler #object[...]},
;                           :handler #object[...],
;                           :middleware []}}]]
```

Note the compiled resuts as third element in the route vector.

# Request-method based routing

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

Name-based reverse routing:

```clj
(-> app
    (ring/get-router)
    (reitit/match-by-name ::ping)
    :path)
; "/ping"
```

# Middleware

Middleware can be added with a `:middleware` key, either to top-level or under `:request-method` submap. It's value should be a vector of any the following:

1. normal ring middleware function `handler -> request -> response`
2. vector of middleware function `[handler args*] -> request -> response` and it's arguments
3. a [data-driven middleware](data_driven_middleware.md) record or a map
4. a Keyword name, to lookup the middleware from a [Middleware Registry](middleware_registry.md)

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
      ;; a middleware function
      ["/api" {:middleware [#(wrap % :api)]}
       ["/ping" handler]
       ;; a middleware vector at top level
       ["/admin" {:middleware [[wrap :admin]]}
        ["/db" {:middleware [[wrap :db]]
                ;; a middleware vector at under a method
                :delete {:middleware [[wrap :delete]]
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

# Async Ring

All built-in middleware provide both 2 and 3-arity and are compiled for both Clojure & ClojureScript, so they work with [Async Ring](https://www.booleanknot.com/blog/2016/07/15/asynchronous-ring.html) and [Node.js](https://nodejs.org) too.
