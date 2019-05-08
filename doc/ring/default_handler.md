# Default handler

By default, if no routes match, `nil` is returned, which is not valid response in Ring:

```clj
(require '[reitit.ring :as ring])

(defn handler [_]
  {:status 200, :body ""})

(def app
  (ring/ring-handler
    (ring/router
      ["/ping" handler])))

(app {:uri "/invalid"})
; nil
```

Setting the default-handler as a second argument to `ring-handler`:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/ping" handler])
    (constantly {:status 404, :body ""})))

(app {:uri "/invalid"})
; {:status 404, :body ""}
```

To get more correct http error responses, `ring/create-default-handler` can be used. It differentiates `:not-found` (no route matched), `:method-not-allowed` (no method matched) and `:not-acceptable` (handler returned `nil`).

With defaults:

```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/ping" {:get handler}]
       ["/pong" (constantly nil)]])
    (ring/create-default-handler)))

(app {:request-method :get, :uri "/ping"})
; {:status 200, :body ""}

(app {:request-method :get, :uri "/"})
; {:status 404, :body ""}

(app {:request-method :post, :uri "/ping"})
; {:status 405, :body ""}

(app {:request-method :get, :uri "/pong"})
; {:status 406, :body ""}
```

With custom responses:

```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/ping" {:get handler}]
       ["/pong" (constantly nil)]])
    (ring/create-default-handler
      {:not-found (constantly {:status 404, :body "kosh"})
       :method-not-allowed (constantly {:status 405, :body "kosh"})
       :not-acceptable (constantly {:status 406, :body "kosh"})})))

(app {:request-method :get, :uri "/ping"})
; {:status 200, :body ""}

(app {:request-method :get, :uri "/"})
; {:status 404, :body "kosh"}

(app {:request-method :post, :uri "/ping"})
; {:status 405, :body "kosh"}

(app {:request-method :get, :uri "/pong"})
; {:status 406, :body "kosh"}
```
