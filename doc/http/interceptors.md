# Interceptors

Reitit has also support for [interceptors](http://pedestal.io/reference/interceptors) as an alternative to using middleware. Basic interceptor handling is implemented in `reitit.interceptor` package.  There is no interceptor executor shipped, but you can use libraries like [Pedestal Interceptor](https://github.com/pedestal/pedestal/tree/master/interceptor) or [Sieppari](https://github.com/metosin/sieppari) to execute the chains.

## Reitit-http

```clj
[metosin/reitit-http "0.5.5"]
```

A module for http-routing using interceptors instead of middleware. Builds on top of the [`reitit-ring`](../ring/ring.md) module having all the same features.

The differences:

* `:interceptors` key used in route data instead of `:middleware`
* `reitit.http/http-router` requires an extra option `:executor` of type `reitit.interceptor/Executor` to execute the interceptor chain
   * optionally, a routing interceptor can be used - it enqueues the matched interceptors into the context. See `reitit.http/routing-interceptor` for details.

## Simple example

```clj
(require '[reitit.ring :as ring])
(require '[reitit.http :as http])
(require '[reitit.interceptor.sieppari :as sieppari])

(defn interceptor [number]
  {:enter (fn [ctx] (update-in ctx [:request :number] (fnil + 0) number))})

(def app
  (http/ring-handler
    (http/router
      ["/api"
       {:interceptors [(interceptor 1)]}

       ["/number"
        {:interceptors [(interceptor 10)]
         :get {:interceptors [(interceptor 100)]
               :handler (fn [req]
                          {:status 200
                           :body (select-keys req [:number])})}}]])

    ;; the default handler
    (ring/create-default-handler)

    ;; executor
    {:executor sieppari/executor}))


(app {:request-method :get, :uri "/"})
; {:status 404, :body "", :headers {}}

(app {:request-method :get, :uri "/api/number"})
; {:status 200, :body {:number 111}}
```

## Why interceptors?

* https://quanttype.net/posts/2018-08-03-why-interceptors.html
* https://www.reddit.com/r/Clojure/comments/9csmty/why_interceptors/
