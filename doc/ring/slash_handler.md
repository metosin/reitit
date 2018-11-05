# Slash handler

The router works with precise matches. If a route is defined without a trailing slash, for example, it won't match a request with a slash.

```clj
(require '[reitit.ring :as ring])

(def app
  (ring/ring-handler
    (ring/router
      ["/ping" (constantly {:status 200, :body ""})])))

(app {:uri "/ping/"})
; nil
```

Sometimes it is desirable that paths with and without a trailing slash are recognized as the same.

Setting the `redirect-trailing-slash-handler` as a second argument to `ring-handler`:

```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200, :body ""})]
       ["/pong/" (constantly {:status 200, :body ""})]])
    (ring/redirect-trailing-slash-handler)))

(app {:uri "/ping/"})
; {:status 308, :headers {"Location" "/ping"}, :body ""}

(app {:uri "/pong"})
; {:status 308, :headers {"Location" "/pong/"}, :body ""}
```

`redirect-trailing-slash-handler` accepts an optional `:method` parameter that allows configuring how (whether) to handle missing/extra slashes. The default is to handle both.

```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200, :body ""})]
       ["/pong/" (constantly {:status 200, :body ""})]])
    ; only handle extra trailing slash
    (ring/redirect-trailing-slash-handler {:method :strip})))

(app {:uri "/ping/"})
; {:status 308, :headers {"Location" "/ping"}, :body ""}

(app {:uri "/pong"})
; nil
```

```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200, :body ""})]
       ["/pong/" (constantly {:status 200, :body ""})]])
    ; only handle missing trailing slash
    (ring/redirect-trailing-slash-handler {:method :add})))

(app {:uri "/ping/"})
; nil

(app {:uri "/pong"})
; {:status 308, :headers {"Location" "/pong/"}, :body ""}
```

`redirect-trailing-slash-handler` can be composed with the default handler using `ring/routes` for more correct http error responses:
```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200, :body ""})]
       ["/pong/" (constantly {:status 200, :body ""})]])
    (ring/routes
      (ring/redirect-trailing-slash-handler {:method :add})
      (ring/create-default-handler))))

(app {:uri "/ping/"})
; {:status 404, :body "", :headers {}}

(app {:uri "/pong"})
; {:status 308, :headers {"Location" "/pong/"}, :body ""}
  ```
