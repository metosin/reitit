# Dynamic extensions

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
