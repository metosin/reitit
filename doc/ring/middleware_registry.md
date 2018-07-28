# Middleware Registry

The `:middleware` syntax in `reitit-ring` supports also Keywords. Keywords are looked from Middleware Registry, which is a map of `keyword => IntoMiddleware`. Middleware registry should be stored under key `:reitit.middleware/registry` in the router options. If a middleware keyword isn't found in the registry, router creation fails fast with descriptive error message.

## Examples 

Application using middleware defined in the Middleware Registry:

```clj
(require '[reitit.ring :as ring])
(require '[reitit.middleware :as middleware])

(defn wrap-bonus [handler value]
  (fn [request]
    (handler (update request :bonus (fnil + 0) value))))

(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[:bonus 20]]}
       ["/bonus" {:middleware [:bonus10]
                 :get (fn [{:keys [bonus]}]
                        {:status 200, :body {:bonus bonus}})}]]
      {::middleware/registry {:bonus wrap-bonus
                              :bonus10 [:bonus 10]}})))
```

Works as expected:

```clj
(app {:request-method :get, :uri "/api/bonus"})
; {:status 200, :body {:bonus 30}}
```

Router creation fails fast if registry doesn't contain the Middleware:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[:bonus 20]]}
       ["/bonus" {:middleware [:bonus10]
                  :get (fn [{:keys [bonus]}]
                         {:status 200, :body {:bonus bonus}})}]]
      {::middleware/registry {:bonus wrap-bonus}})))
;CompilerException clojure.lang.ExceptionInfo: Middleware :bonus10 not found in registry.
;
;Available middleware in registry:
;
;|    :id |                         :description |
;|--------+--------------------------------------|
;| :bonus | reitit.ring_test$wrap_bonus@59fddabb |
```

## When to use the registry?

Middleware as Keywords helps to keep the routes (all but handlers) as literal data (e.g. data that evaluates to itself) enabling the routes to be persisted in external formats like EDN-files and databases. Duct is a good example where the [middleware can be referenced from EDN-files](https://github.com/duct-framework/duct/wiki/Configuration). It should be easy to make Duct configuration a Middleware Registry in `reitit-ring`.

The bad thing it's an extra indirection, making things more complex and removed the default IDE-support of "goto definition" or "look source".

## TODO

* a prefilled registry of common middleware in the `reitit-middleware`
