# Introduction

[Reitit](https://github.com/metosin/reitit) is a small Clojure(Script) library for data-driven routing.

* Simple data-driven [route syntax](./basics/route_syntax.md)
* [Route conflict resolution](./advanced/route_conflicts.md)
* First-class [route meta-data](./basics/route_data.md)
* Bi-directional routing
* [Pluggable coercion](./ring/parameter_coercion.md) ([clojure.spec](https://clojure.org/about/spec))
* supports both [Middleware](./ring/compiling_middleware.md) & Interceptors
* Extendable
* [Fast](performance.md)

To use Reitit, add the following dependecy to your project:

```clj
[metosin/reitit "0.1.0-SNAPSHOT"]
```

Optionally, the parts can be required separately:

```clj
[metosin/reitit-core "0.1.0-SNAPSHOT"] ; just the router
[metosin/reitit-ring "0.1.0-SNAPSHOT"] ; ring-router
[metosin/reitit-spec "0.1.0-SNAPSHOT"] ; spec-coercion
```

# Examples

## Simple router

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/api/ping" ::ping]
     ["/api/orders/:id" ::order-by-id]]))
```

Routing:

```clj
(r/match-by-path router "/api/ipa")
; nil

(r/match-by-path router "/api/ping")
; #Match{:template "/api/ping"
;        :meta {:name ::ping}
;        :result nil
;        :params {}
;        :path "/api/ping"}

(r/match-by-path router "/api/orders/1")
; #Match{:template "/api/orders/:id"
;        :meta {:name ::order-by-id}
;        :result nil
;        :params {:id "1"}
;        :path "/api/orders/1"}
```

Reverse-routing:

```clj
(r/match-by-name router ::ipa)
; nil

(r/match-by-name router ::ping)
; #Match{:template "/api/ping"
;        :meta {:name ::ping}
;        :result nil
;        :params {}
;        :path "/api/ping"}

(r/match-by-name router ::order-by-id)
; #PartialMatch{:template "/api/orders/:id"
;               :meta {:name :user/order-by-id}
;               :result nil
;               :params nil
;               :required #{:id}}

(r/partial-match? (r/match-by-name router ::order-by-id))
; true

(r/match-by-name router ::order-by-id {:id 2})
; #Match{:template "/api/orders/:id",
;        :meta {:name ::order-by-id},
;        :result nil,
;        :params {:id 2},
;        :path "/api/orders/2"}
```

## Ring-router

Ring-router adds support for `:handler` functions, `:middleware` and routing based on `:request-method`. It also supports pluggable parameter coercion (`clojure.spec`), data-driven middleware, route and middleware compilation, dynamic extensions and more.

```clj
(require '[reitit.ring :as ring])

(def handler [_]
  {:status 200, :body "ok"})

(defn wrap [handler id]
  (fn [request]
    (update (handler request) :wrap (fnil conj '()) id)))

(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[wrap :api]]}
       ["/ping" {:get handler
                 :name ::ping}]
       ["/admin" {:middleware [[wrap :admin]]}
        ["/users" {:get handler
                   :post handler}]]])))
```

Routing:

```clj
(app {:request-method :get, :uri "/api/admin/users"})
; {:status 200, :body "ok", :wrap (:api :admin}

(app {:request-method :put, :uri "/api/admin/users"})
; nil
```

Reverse-routing:

```clj
(require '[reitit.core :as r])

(-> app (ring/get-router) (r/match-by-name ::ping))
; #Match{:template "/api/ping"
;        :meta {:middleware [[#object[user$wrap] :api]]
;               :get {:handler #object[user$handler]}
;        :name ::ping}
;        :result #Methods{...}
;        :params nil
;        :path "/api/ping"}
```
