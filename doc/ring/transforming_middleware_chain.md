# Transformation Middleware Chain

There is an extra option in ring-router (actually, in the underlying middleware-router): `:reitit.middleware/transform` to transform the middleware chain per endpoint. It gets the vector of compiled middleware and should return a new vector of middleware.

## Adding debug middleware between all other middleware

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap 1] [wrap2 2]]}
       ["/ping" {:get {:middleware [[wrap3 3]]
                       :handler handler}}]]
      {::middleware/transform #(interleave % (repeat [wrap :debug]))})))
```

```clj
(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body [1 :debug 2 :debug 3 :debug :handler]}
```

## Reversing the middleware chain

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap 1] [wrap2 2]]}
       ["/ping" {:get {:middleware [[wrap3 3]]
                       :handler handler}}]]
      {::middleware/transform reverse)})))
```

```clj
(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body [3 2 1 :handler]}
```
