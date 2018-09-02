# Interceptors (WIP)

Reitit also support for [Pedestal](pedestal.io)-style [interceptors](http://pedestal.io/reference/interceptors) as an alternative to Middleware. Basic interceptor handling is implemented in `reitit.interceptor` package.  There is no interceptor executor shipped, but you can use libraries like [Pedestal Interceptor](https://github.com/pedestal/pedestal/tree/master/interceptor) or [Sieppari](https://github.com/metosin/sieppari) to execute the chains.

## Reitit-http

An alternative to `reitit-ring`, using interceptors instead of middleware. Currently not finalized, you can track progress in [here](https://github.com/metosin/reitit/pull/124).

## Examples

### Standalone

* [Sieppari](https://github.com/metosin/sieppari) for executing the chain
* [Manifold](https://github.com/ztellman/manifold) for async
* [data-specs](https://github.com/metosin/spec-tools/blob/master/README.md#data-specs) for coercion

```clj
(require '[reitit.interceptor.sieppari :as sieppari])
(require '[reitit.http.coercion :as coercion])
(require '[reitit.http :as http])
(require '[reitit.ring :as ring])
(require '[reitit.coercion.spec])
(require '[clojure.set :as set])
(require '[manifold.deferred :as d])
(require '[ring.adapter.jetty :as jetty])

(def auth-interceptor
  "Interceptor that mounts itself if route has `:roles` data. Expects `:roles`
  to be a set of keyword and the context to have `[:user :roles]` with user roles.
  responds with HTTP 403 if user doesn't have the roles defined, otherwise no-op."
  {:name ::auth
   :compile (fn [{:keys [roles]} _]
              (if (seq roles)
                {:description (str "requires roles " roles)
                 :spec {:roles #{keyword?}}
                 :context-spec {:user {:roles #{keyword}}}
                 :enter (fn [{{user-roles :roles} :user :as ctx}]
                          (if (not (set/subset? roles user-roles))
                            (assoc ctx :response {:status 403, :body "forbidden"})
                            ctx))}))})

(def async-interceptor
  {:enter (fn [ctx] (d/future ctx))})

(def app
  (http/ring-handler
    (http/router
      ["/api" {:interceptors [async-interceptor auth-interceptor]}
       ["/ping" {:name ::ping
                 :get (constantly
                        {:status 200
                         :body "pong"})}]
       ["/plus/:z" {:name ::plus
                    :post {:parameters {:query {:x int?}
                                        :body {:y int?}
                                        :path {:z int?}}
                           :responses {200 {:body {:total pos-int?}}}
                           :roles #{:admin}
                           :handler (fn [{:keys [parameters]}]
                                      (let [total (+ (-> parameters :query :x)
                                                     (-> parameters :body :y)
                                                     (-> parameters :path :z))]
                                        {:status 200
                                         :body {:total total}}))}}]]
      {:data {:coercion reitit.coercion.spec/coercion
              :interceptors [coercion/coerce-exceptions-interceptor
                             coercion/coerce-request-interceptor
                             coercion/coerce-response-interceptor]}})
    (ring/create-default-handler)
    {:executor sieppari/executor}))

(jetty/run-jetty #'app {:port 3000, :join? false, :async? true})
```

### Pedestal

**TODO**
