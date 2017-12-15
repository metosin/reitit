# Ring Coercion

Coercion is explained in detail [in the Coercion Guide](../coercion/coercion.md). Both request parameters (`:query`, `:body`, `:form`, `:header` and `:path`) and response `:body` can be coerced.

To enable coercion, the following things need to be done:

* Define a `reitit.coercion/Coercion` for the routes
* Define types for the parameters and/or responses
* Mount Coercion Middleware to apply to coercion
* Use the coerced parameters in a handler/middleware

## Define coercion

`reitit.coercion/Coercion` is a protocol defining how types are defined, coerced and inventoried.

Reitit ships with the following coercion modules:

* `reitit.coercion.schema/coercion` for [plumatic schema](https://github.com/plumatic/schema)
* `reitit.coercion.spec/coercion` for both [clojure.spec](https://clojure.org/about/spec) and [data-specs](https://github.com/metosin/spec-tools#data-specs)

Coercion can be attached to route data under `:coercion` key. There can be multiple `Coercion` implementations within a single router, normal [scoping rules](../basics/route_data.html#nested-route-data) apply.

# Defining parameters and responses

Below is a ring route data defining [Plumatic Schema](https://github.com/plumatic/schema) coercion. It defines schemas for `:query`, `:body` and  `:path` parameters and for a successful response `:body`.

The coerced parameters can be read under `:parameters` key in the request.

```clj
(require '[reitit.coercion.schema])
(require '[schema.core :as s])

(def plus-endpoint
  {:coercion reitit.coercion.schema/coercion
   :parameters {:query {:x s/Int}
                :body {:y s/Int}
                :path {:z s/Int}}
   :responses {200 {:schema {:total PositiveInt}}}
   :handler (fn [{:keys [parameters]}]
              (let [total (+ (-> parameters :query :x)
                             (-> parameters :body :y)
                             (-> parameters :path :z))]
                {:status 200
                 :body {:total total}}))})
```

## Coercion Middleware

Defining a coercion for a route data doesn't do anything, as it's just data. We have to attach some code to apply the actual coercion. We can use the middleware from `reitit.ring.coercion-middleware`:

* `coerce-request-middleware` for the parameter coercion
* `coerce-response-middleware` for the response coercion
* `coerce-exceptions-middleware` to turn coercion exceptions into pretty responses

### Full example

Here's an full example for applying coercion with Reitit, Ring and Schema:

```clj
(require '[reitit.ring.coercion-middleware :as mw])
(require '[reitit.coercion.schema])
(require '[reitit.ring :as ring])
(require '[schema.core :as s])

(def PositiveInt (s/constrained s/Int pos? 'PositiveInt))

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/ping" {:name ::ping
                 :get (fn [_]
                        {:status 200
                         :body "pong"})}]
       ["/plus/:z" {:name ::plus
                    :post {:coercion reitit.coercion.schema/coercion
                           :parameters {:query {:x s/Int}
                                        :body {:y s/Int}
                                        :path {:z s/Int}}
                           :responses {200 {:schema {:total PositiveInt}}}
                           :handler (fn [{:keys [parameters]}]
                                      (let [total (+ (-> parameters :query :x)
                                                     (-> parameters :body :y)
                                                     (-> parameters :path :z))]
                                        {:status 200
                                         :body {:total total}}))}}]]
      {:data {:middleware [mw/coerce-exceptions-middleware
                           mw/coerce-request-middleware
                           mw/coerce-response-middleware]}})))
```

Valid request:

```clj
(app {:request-method :post
      :uri "/api/plus/3"
      :query-params {"x" "1"}
      :body-params {:y 2}})
; {:status 200, :body {:total 6}}
```

Invalid request:

```clj
(app {:request-method :post
      :uri "/api/plus/3"
      :query-params {"x" "abba"}
      :body-params {:y 2}})
; {:status 400,
;  :body {:schema {:x "Int", "Any" "Any"},
;         :errors {:x "(not (integer? \"abba\"))"},
;         :type :reitit.coercion/request-coercion,
;         :coercion :schema,
;         :value {:x "abba"},
;         :in [:request :query-params]}}
```

Invalid response:

```clj
(app {:request-method :post
      :uri "/api/plus/3"
      :query-params {"x" "1"}
      :body-params {:y -10}})
; {:status 500,
;  :body {:schema {:total "(constrained Int PositiveInt)"},
;         :errors {:total "(not (PositiveInt -6))"},
;         :type :reitit.coercion/response-coercion,
;         :coercion :schema,
;         :value {:total -6},
;         :in [:response :body]}}
```

### Optimizations

The coercion middleware are [compiled againts a route](compiling_middleware,md). In the compile step the actual coercer implementations are compiled for the defined models. Also, the mw doesn't mount itself if a route doesn't have `:coercion` and `:parameters` or `:responses` defined.

We can query the compiled middleware chain for the routes:

```clj
(require '[reitit.core :as r])

(-> (ring/get-router app)
    (r/match-by-name ::plus)
    :result :post :middleware
    (->> (mapv :name)))
; [::mw/coerce-exceptions
;  ::mw/coerce-parameters
;  ::mw/coerce-response]
```

Route without coercion defined:

```clj
(app {:request-method :get, :uri "/api/ping"})
; {:status 200, :body "pong"}
```

Has no mounted middleware:

```clj
(-> (ring/get-router app)
    (r/match-by-name ::ping)
    :result :get :middleware
    (->> (mapv :name)))
; []
```
## Thanks to

* [compojure-api](https://clojars.org/metosin/compojure-api) for the initial `Coercion` protocol
* [ring-swagger](https://github.com/metosin/ring-swagger#more-complete-example) for the `:parameters` and `:responses` syntax.
* [schema](https://github.com/plumatic/schema) and [schema-tools](https://github.com/metosin/schema-tools) for Schema Coercion
* [spec-tools](https://github.com/metosin/spec-tools) for Spec Coercion
