# Dynamic Extensions

`ring-handler` injects the `Match` into a request and it can be extracted at runtime with `reitit.ring/get-match`. This can be used to build ad-hoc extensions to the system.

Example middleware to guard routes based on user roles:

```clj
(require '[reitit.ring :as ring])
(require '[clojure.set :as set])

(defn wrap-enforce-roles [handler]
  (fn [{:keys [my-roles] :as request}]
    (let [required (some-> request (ring/get-match) :data ::roles)]
      (if (and (seq required) (not (set/subset? required my-roles)))
        {:status 403, :body "forbidden"}
        (handler request)))))
```

Mounted to an app via router data (affecting all routes):

```clj
(def handler (constantly {:status 200, :body "ok"}))

(def app
  (ring/ring-handler
    (ring/router
      [["/api"
        ["/ping" handler]
        ["/admin" {::roles #{:admin}}
         ["/ping" handler]]]]
      {:data {:middleware [wrap-enforce-roles]}})))
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
(app {:request-method :get, :uri "/api/admin/ping", :my-roles #{:admin}})
; {:status 200, :body "ok"}
```

Dynamic extensions are nice, but we can do much better. See [data-driven middleware](data_driven_middleware.md) and [compiling routes](compiling_middleware.md).
