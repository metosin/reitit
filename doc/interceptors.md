# Interceptors

Reitit also supports [Pedestal](pedestal.io)-style [interceptors](http://pedestal.io/reference/interceptors) via `reitit.interceptor` package.

Full support is WIP at the moment:

* separate module (or library?) for interceptor interpreters
* figure out how to make a truly portable Interceptor definitions, e.g. Pedestal has namespaced keys for context errors, queues etc.
* finalize `reitit-http` module as an alternative to `reitit-ring`

Current `reitit-http` draft (with data-specs):

```clj
(require '[reitit.http.coercion :as rhc])
(require '[reitit.http :as http])
(require '[reitit.coercion.spec])
(require '[clojure.set :as set])

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
                            ctx))}))})(require '[clojure.set :as set])

(def app
  (http/http-handler
    (http/router
      ["/api" {:interceptors [auth-interceptor]}
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
              :interceptors [rhc/coerce-exceptions-interceptor
                             rhc/coerce-request-interceptor
                             rhc/coerce-response-interceptor]}})))
```
