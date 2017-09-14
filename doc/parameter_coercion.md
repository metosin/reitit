# Parameter coercion

Reitit provides pluggable parameter coercion via `reitit.coercion.protocol/Coercion` protocol, originally introduced in [compojure-api](https://clojars.org/metosin/compojure-api). Reitit ships with `reitit.coercion.spec/SpecCoercion` providing implemenation for [clojure.spec](https://clojure.org/about/spec) and [data-specs](https://github.com/metosin/spec-tools#data-specs).

**NOTE**: Before Clojure 1.9.0 is shipped, to use the spec-coercion, one needs to add the following dependencies manually to the project:

```clj
[org.clojure/clojure "1.9.0-alpha20"]
[org.clojure/spec.alpha "0.1.123"]
[metosin/spec-tools "0.3.3"]
```

### Ring request and response coercion

To use `Coercion` with Ring, one needs to do the following:

1. Define parameters and responses as data into route meta-data, in format adopted from [ring-swagger](https://github.com/metosin/ring-swagger#more-complete-example):
  * `:parameters` map, with submaps for different parameters: `:query`, `:body`, `:form`, `:header` and `:path`. Parameters are defined in the format understood by the `Coercion`.
  * `:responses` map, with response status codes as keys (or `:default` for "everything else") with maps with `:schema` and optionally `:description` as values.
2. Define a `Coercion` to route meta-data under `:coercion`
3. Mount request & response coercion middleware to the routes.

If the request coercion succeeds, the coerced parameters are injected into request under `:parameters`.

If either request or response coercion fails, an descriptive error is thrown.

#### Example with data-specs

```clj
(require '[reitit.ring :as ring])
(require '[reitit.coercion :as coercion])
(require '[reitit.coercion.spec :as spec])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/ping" {:parameters {:body {:x int?, :y int?}}
                 :responses {200 {:schema {:total pos-int?}}}
                 :get {:handler (fn [{{{:keys [x y]} :body} :parameters}]
                                  {:status 200
                                   :body {:total (+ x y)}})}}]]
      {:meta {:middleware [coercion/gen-wrap-coerce-parameters
                           coercion/gen-wrap-coerce-response]
              :coercion spec/coercion}})))
```


```clj
(app
  {:request-method :get
   :uri "/api/ping"
   :body-params {:x 1, :y 2}})
; {:status 200, :body {:total 3}}
```

#### Example with specs

```clj
(require '[reitit.ring :as ring])
(require '[reitit.coercion :as coercion])
(require '[reitit.coercion.spec :as spec])
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.core :as st])

(s/def ::x (st/spec int?))
(s/def ::y (st/spec int?))
(s/def ::total int?)
(s/def ::request (s/keys :req-un [::x ::y]))
(s/def ::response (s/keys :req-un [::total]))

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/ping" {:parameters {:body ::request}
                 :responses {200 {:schema ::response}}
                 :get {:handler (fn [{{{:keys [x y]} :body} :parameters}]
                                  {:status 200
                                   :body {:total (+ x y)}})}}]]
      {:meta {:middleware [coercion/gen-wrap-coerce-parameters
                           coercion/gen-wrap-coerce-response]
              :coercion spec/coercion}})))
```

```clj
(app
  {:request-method :get
   :uri "/api/ping"
   :body-params {:x 1, :y 2}})
; {:status 200, :body {:total 3}}
```
