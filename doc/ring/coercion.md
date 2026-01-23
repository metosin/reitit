# Ring Coercion

Basic coercion is explained in detail [in the Coercion Guide](../coercion/coercion.md). With Ring, both request parameters and response bodies can be coerced.

The following request parameters are currently supported:

| type         | request source                                   |
|--------------|--------------------------------------------------|
| `:query`     | `:query-params`                                  |
| `:body`      | `:body-params`                                   |
| `:request`   | `:body-params`, allows per-content-type coercion |
| `:form`      | `:form-params`                                   |
| `:header`    | `:header-params`                                 |
| `:path`      | `:path-params`                                   |
| `:multipart` | `:multipart-params`, see [Default Middleware](default_middleware.md) |

To enable coercion, the following things need to be done:

* Define a `reitit.coercion/Coercion` for the routes
* Define types for the parameters and/or responses
* Mount Coercion Middleware to apply to coercion
* Use the coerced parameters in a handler/middleware

## Define coercion

`reitit.coercion/Coercion` is a protocol defining how types are defined, coerced and inventoried.

Reitit ships with the following coercion modules:

* `reitit.coercion.malli/coercion` for [malli](https://github.com/metosin/malli)
* `reitit.coercion.schema/coercion` for [plumatic schema](https://github.com/plumatic/schema)
* `reitit.coercion.spec/coercion` for both [clojure.spec](https://clojure.org/about/spec) and [data-specs](https://github.com/metosin/spec-tools#data-specs)

Coercion can be attached to route data under `:coercion` key. There can be multiple `Coercion` implementations within a single router, normal [scoping rules](../basics/route_data.md#nested-route-data) apply.

## Defining parameters and responses

Parameters are defined in route data under `:parameters` key. It's value should be a map of parameter `:type` -> Coercion Schema.

Responses are defined in route data under `:responses` key. It's value should be a map of http status code to a map which can contain `:body` key with Coercion Schema as value. Additionally, the key `:default` specifies the coercion for other status codes.

Below is an example with [Plumatic Schema](https://github.com/plumatic/schema). It defines schemas for `:query`, `:body` and `:path` parameters and for http 200 response `:body`.

Handlers can access the coerced parameters via the `:parameters` key in the request.

```clj
(require '[reitit.coercion.schema])
(require '[schema.core :as s])

(def PositiveInt (s/constrained s/Int pos? 'PositiveInt))

(def plus-endpoint
  {:coercion reitit.coercion.schema/coercion
   :parameters {:query {:x s/Int}
                :body {:y s/Int}
                :path {:z s/Int}}
   :responses {200 {:body {:total PositiveInt}}
               :default {:body {:error s/Str}}}
   :handler (fn [{:keys [parameters]}]
              (let [total (+ (-> parameters :query :x)
                             (-> parameters :body :y)
                             (-> parameters :path :z))]
                {:status 200
                 :body {:total total}}))})
```
### Nested parameter definitions

Parameters are accumulated recursively along the route tree, just like
other [route data](../basics/route_data.md). There is special case
handling for merging eg. malli `:map` schemas.

```clj
(def router
 (reitit.ring/router
   ["/api" {:get {:parameters {:query [:map [:api-key :string]]}}}
    ["/project/:project-id" {:get {:parameters {:path [:map [:project-id :int]]}}}
     ["/task/:task-id" {:get {:parameters {:path [:map [:task-id :int]]
                                           :query [:map [:details :boolean]]}
                              :handler (fn [req] (prn req))}}]]]
   {:data {:coercion reitit.coercion.malli/coercion}}))
```

```clj
(-> (r/match-by-path router "/api/project/1/task/2") :result :get :data :parameters)
; {:query [:map
;          {:closed true}
;          [:api-key :string]
;          [:details :boolean]],
;  :path [:map
;         {:closed true}
;         [:project-id :int]
;         [:task-id :int]]}
```

### Differences in behaviour for different parameters

All parameter coercions *except* `:body`:

1. Allow keys outside the schema (by opening up the schema using eg. `malli.util/open-schema`)
2. Keywordize the keys (ie. header & query parameter names) of the input before coercing

In contrast, the `:body` coercion:

1. Uses the specified schema
   * depending on the coercion, it can be configured as open or closed, see specific coercion docs for details
2. Does not keywordize the keys of the input before coercion
   * however, coercions like malli might do the keywordization when coercing json bodies, depending on configuration

This admittedly confusing behaviour is retained currently due to
backwards compatibility reasons. It can be configured by passing
option `:reitit.coercion/parameter-coercion` to `reitit.ring/router`
or `reitit.coercion/compile-request-coercers`. See also:
`reitit.coercion/default-parameter-coercion`.

## Coercion Middleware

Defining a coercion for a route data doesn't do anything, as it's just data. We have to attach some code to apply the actual coercion. We can use the middleware from `reitit.ring.coercion`:

* `coerce-request-middleware` to apply the parameter coercion
* `coerce-response-middleware` to apply the response coercion
* `coerce-exceptions-middleware` to transform coercion exceptions into pretty responses

### Full example

Here is a full example for applying coercion with Reitit, Ring and Schema:

```clj
(require '[reitit.ring.coercion :as rrc])
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
                           :responses {200 {:body {:total PositiveInt}}}
                           :handler (fn [{:keys [parameters]}]
                                      (let [total (+ (-> parameters :query :x)
                                                     (-> parameters :body :y)
                                                     (-> parameters :path :z))]
                                        {:status 200
                                         :body {:total total}}))}}]]
      {:data {:middleware [rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))
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

## Per-content-type coercion

You can also specify request and response body schemas per
content-type. These are also read by the [OpenAPI
feature](./openapi.md) when generating api docs. The syntax for this
is:

```clj
(def app
  (ring/ring-handler
   (ring/router
    ["/api"
     ["/example" {:post {:coercion reitit.coercion.schema/coercion
                         :request {:content {"application/json" {:schema {:y s/Int}}
                                             "application/edn" {:schema {:z s/Int}}
                                             ;; default if no content-type matches:
                                             :default {:schema {:yy s/Int}}}}
                         :responses {200 {:content {"application/json" {:schema {:w s/Int}}
                                                    "application/edn" {:schema {:x s/Int}}
                                                    :default {:schema {:ww s/Int}}}}}
                         :handler ...}}]]
    {:data {:muuntaja muuntaja.core/instance
            :middleware [reitit.ring.middleware.muuntaja/format-middleware
                         reitit.ring.coercion/coerce-exceptions-middleware
                         reitit.ring.coercion/coerce-request-middleware
                         reitit.ring.coercion/coerce-response-middleware]}})))
```

The resolution logic for response coercers is:
1. Get the response status, or `:default` from the `:responses` map
2. From this map, get use the first of these to coerce:
   1. `:content <content-type> :schema`
   2. `:content :default :schema`
   3. `:body`
3. If nothing was found, do not coerce

To select the response content-type, you can either:
1. Let muuntaja pick the content-type based on things like the request Accept header
   - This is what most users want
2. Set `:muuntaja/content-type` in the response to pick an explicit content type
3. Set the `"Content-Type"` header in the response
   - This disables muuntaja, so you need to encode your response body in some other way!
   - This is not compatible with response schema checking, since coercion won't know what to do with the already-encoded response body.
4. Use the `:extract-response-format` option to inject your own logic. See `reitit.coercion/extract-response-format-default` for the default.

See also the [muuntaja content negotiation](./content_negotiation.md) docs.

## Pretty printing spec errors

Spec problems are exposed as is in request & response coercion errors. Pretty-printers like [expound](https://github.com/bhb/expound) can be enabled like this:

```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.middleware.exception :as exception])
(require '[reitit.ring.coercion :as coercion])
(require '[expound.alpha :as expound])

(defn coercion-error-handler [status]
  (let [printer (expound/custom-printer {:theme :figwheel-theme, :print-specs? false})
        handler (exception/create-coercion-handler status)]
    (fn [exception request]
      (printer (-> exception ex-data :problems))
      (handler exception request))))

(def app
  (ring/ring-handler
    (ring/router
      ["/plus"
       {:get
        {:parameters {:query {:x int?, :y int?}}
         :responses {200 {:body {:total pos-int?}}}
         :handler (fn [{{{:keys [x y]} :query} :parameters}]
                    {:status 200, :body {:total (+ x y)}})}}]
      {:data {:coercion reitit.coercion.spec/coercion
              :middleware [(exception/create-exception-middleware
                             (merge
                               exception/default-handlers
                               {:reitit.coercion/request-coercion (coercion-error-handler 400)
                                :reitit.coercion/response-coercion (coercion-error-handler 500)}))
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware]}})))

(app
  {:uri "/plus"
   :request-method :get
   :query-params {"x" "1", "y" "fail"}})
; => ...
; -- Spec failed --------------------
;
;   {:x ..., :y "fail"}
;                ^^^^^^
;
; should satisfy
;
;   int?



(app
  {:uri "/plus"
   :request-method :get
   :query-params {"x" "1", "y" "-2"}})
; => ...
;-- Spec failed --------------------
;
;   {:total -1}
;           ^^
;
; should satisfy
;
;   pos-int?
```

### Optimizations

The coercion middlewares are [compiled against a route](compiling_middleware.md). In the middleware compilation step the actual coercer implementations are constructed for the defined models. Also, the middleware doesn't mount itself if a route doesn't have `:coercion` and `:parameters` or `:responses` defined.

We can query the compiled middleware chain for the routes:

```clj
(require '[reitit.core :as r])

(-> (ring/get-router app)
    (r/match-by-name ::plus)
    :result :post :middleware
    (->> (mapv :name)))
; [::mw/coerce-exceptions
;  ::mw/coerce-request
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
