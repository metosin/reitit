# Sieppari

```clj
[metosin/reitit-sieppari "0.2.1"]
```

[Sieppari](https://github.com/metosin/sieppari) is a new and fast interceptor implementation with pluggable async ([core.async](https://github.com/clojure/core.async), [Manifold](https://github.com/ztellman/manifold) and [Promesa](http://funcool.github.io/promesa/latest)).

To use Sieppari with `reitit-http`, there is `reitit-sieppari` module, which has an `reitit.interceptor.Executor` implementation for Sieppari. All reitit interceptors use the Sieppari Interceptor model, so they work seamlesly together.

Synchronous Ring:

```clj
(require '[reitit.http :as http])
(require '[reitit.interceptor.sieppari :as sieppari])

(defn i [x]
  {:enter (fn [ctx] (println "enter " x) ctx)
   :leave (fn [ctx] (println "leave " x) ctx)})

(defn handler [_]
  (future {:status 200, :body "pong"}))

(def app
  (http/ring-handler
    (http/router
      ["/api"
       {:interceptors [(i :api)]}

       ["/ping"
        {:interceptors [(i :ping)]
         :get {:interceptors [(i :get)]
               :handler handler}}]])
    {:executor sieppari/executor}))

(app {:request-method :get, :uri "/api/ping"})
;enter  :api
;enter  :ping
;enter  :get
;leave  :get
;leave  :ping
;leave  :api
;=> {:status 200, :body "pong"}
```

Ring-async:

```clj
(let [respond (promise)]
  (app {:request-method :get, :uri "/api/ping"} respond nil)
  (deref respond 1000 ::timeout))
;enter  :api
;enter  :ping
;enter  :get
;leave  :get
;leave  :ping
;leave  :api
;=> {:status 200, :body "pong"}
```

## Examples

### Simple

* simple example, with both sync & async code:
  * https://github.com/metosin/reitit/tree/master/examples/http

### With batteries

* with [default interceptors](default_interceptors.md), [coercion](../coercion/coercion.md) and [swagger](../ring/swagger.md)-support:
  * https://github.com/metosin/reitit/tree/master/examples/http-swagger
