# Introduction

[Reitit](https://github.com/metosin/reitit) is a fast data-driven router for Clojure(Script).

* Simple data-driven [route syntax](./basics/route_syntax.md)
* [Route conflict resolution](./basics/route_conflicts.md)
* First-class [route data](./basics/route_data.md)
* Bi-directional routing
* [Pluggable coercion](./coercion/coercion.md) ([schema](https://github.com/plumatic/schema) & [clojure.spec](https://clojure.org/about/spec))
* Extendable
* Modular
* [Fast](performance.md)

The following higher-level routers are also available as separate modules:
* [`ring-router`](./ring/ring.md) with [data-driven middleware](./ring/data_driven_middleware.md)
* `http-router` with enchanced Pedestal-style Interceptors (WIP)
* `frontend-router` with Keechma-style Controllers (WIP)

To use Reitit, add the following dependecy to your project:

```clj
[metosin/reitit "0.1.0-SNAPSHOT"]
```

Optionally, the parts can be required separately:

```clj
[metosin/reitit-core "0.1.0-SNAPSHOT"] ; routing core
[metosin/reitit-ring "0.1.0-SNAPSHOT"] ; ring-router
[metosin/reitit-spec "0.1.0-SNAPSHOT"] ; spec coercion
[metosin/reitit-schema "0.1.0-SNAPSHOT"] ; schema coercion
```

For discussions, there is a [#reitit](https://clojurians.slack.com/messages/reitit/) channel in [Clojurians slack](http://clojurians.net/).

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
;        :data {:name ::ping}
;        :result nil
;        :path-params {}
;        :path "/api/ping"}

(r/match-by-path router "/api/orders/1")
; #Match{:template "/api/orders/:id"
;        :data {:name ::order-by-id}
;        :result nil
;        :path-params {:id "1"}
;        :path "/api/orders/1"}
```

Reverse-routing:

```clj
(r/match-by-name router ::ipa)
; nil

(r/match-by-name router ::ping)
; #Match{:template "/api/ping"
;        :data {:name ::ping}
;        :result nil
;        :path-params {}
;        :path "/api/ping"}

(r/match-by-name router ::order-by-id)
; #PartialMatch{:template "/api/orders/:id"
;               :data {:name :user/order-by-id}
;               :result nil
;               :path-params nil
;               :required #{:id}}

(r/partial-match? (r/match-by-name router ::order-by-id))
; true

(r/match-by-name router ::order-by-id {:id 2})
; #Match{:template "/api/orders/:id",
;        :data {:name ::order-by-id},
;        :result nil,
;        :path-params {:id 2},
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
;        :data {:middleware [[#object[user$wrap] :api]]
;               :get {:handler #object[user$handler]}
;        :name ::ping}
;        :result #Methods{...}
;        :path-params nil
;        :path "/api/ping"}
```
