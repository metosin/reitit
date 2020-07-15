# Transforming the Middleware Chain

There is an extra option in ring-router (actually, in the underlying middleware-router): `:reitit.middleware/transform` to transform the middleware chain per endpoint. Value should be a function or a vector of functions that get a vector of compiled middleware and should return a new vector of middleware.

## Example Application

```clj
(require '[reitit.ring :as ring])
(require '[reitit.middleware :as middleware])

(defn wrap [handler id]
  (fn [request]
    (handler (update request ::acc (fnil conj []) id))))

(defn handler [{::keys [acc]}]
  {:status 200, :body (conj acc :handler)})

(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap 1] [wrap 2]]}
       ["/ping" {:get {:middleware [[wrap 3]]
                       :handler handler}}]])))

(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body [1 2 3 :handler]}
```

### Reversing the Middleware Chain

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap 1] [wrap 2]]}
       ["/ping" {:get {:middleware [[wrap 3]]
                       :handler handler}}]]
      {::middleware/transform reverse})))

(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body [3 2 1 :handler]}
```

## Interleaving Middleware

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap 1] [wrap 2]]}
       ["/ping" {:get {:middleware [[wrap 3]]
                       :handler handler}}]]
      {::middleware/transform #(interleave % (repeat [wrap :debug]))})))

(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body [1 :debug 2 :debug 3 :debug :handler]}
```

### Printing Request Diffs

```clj
[metosin/reitit-middleware "0.5.5"]
```

Using `reitit.ring.middleware.dev/print-request-diffs` transformation, the request diffs between each middleware are printed out to the console. To use it, add the following router option:

```clj
:reitit.middleware/transform reitit.ring.middleware.dev/print-request-diffs
```

Sample output:

![Ring Request Diff](../images/ring-request-diff.png)

