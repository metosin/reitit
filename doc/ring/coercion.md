# Pluggable Coercion

Reitit provides pluggable parameter coercion via `reitit.ring.coercion.protocol/Coercion` protocol, originally introduced in [compojure-api](https://clojars.org/metosin/compojure-api).

Reitit ships with the following coercion modules:

* `reitit.ring.coercion.schema/SchemaCoercion` for [plumatic schema](https://github.com/plumatic/schema).
* `reitit.ring.coercion.spec/SpecCoercion` for both [clojure.spec](https://clojure.org/about/spec) and [data-specs](https://github.com/metosin/spec-tools#data-specs).

### Ring request and response coercion

To use `Coercion` with Ring, one needs to do the following:

1. Define parameters and responses as data into route data, in format adopted from [ring-swagger](https://github.com/metosin/ring-swagger#more-complete-example):
  * `:parameters` map, with submaps for different parameters: `:query`, `:body`, `:form`, `:header` and `:path`. Parameters are defined in the format understood by the `Coercion`.
  * `:responses` map, with response status codes as keys (or `:default` for "everything else") with maps with `:schema` and optionally `:description` as values.
2. Set a `Coercion` implementation to route data under `:coercion`
3. Mount request & response coercion middleware to the routes (can be done for all routes as the middleware are only mounted to routes which have the parameters &/ responses defined):
  * `reitit.ring.coercion/gen-wrap-coerce-parameters`
  * `reitit.ring.coercion/gen-wrap-coerce-response`

If the request coercion succeeds, the coerced parameters are injected into request under `:parameters`.

If either request or response coercion fails, an descriptive error is thrown. To turn the exceptions into http responses, one can also mount the `reitit.ring.coercion/gen-wrap-coerce-exceptions` middleware

### Example with Schema

```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.coercion :as coercion])
(require '[reitit.ring.coercion.schema :as schema])
(require '[schema.core :as s])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/ping" {:post {:parameters {:body {:x s/Int, :y s/Int}}
                        :responses {200 {:schema {:total (s/constrained s/Int pos?}}}
                        :handler (fn [{{{:keys [x y]} :body} :parameters}]
                                   {:status 200
                                    :body {:total (+ x y)}})}}]]
      {:data {:middleware [coercion/gen-wrap-coerce-exceptions
                           coercion/gen-wrap-coerce-parameters
                           coercion/gen-wrap-coerce-response]
              :coercion schema/coercion}})))
```

Valid request:

```clj
(app
  {:request-method :post
   :uri "/api/ping"
   :body-params {:x 1, :y 2}})
; {:status 200
;  :body {:total 3}}
```

Invalid request:

```clj
(app
  {:request-method :post
   :uri "/api/ping"
   :body-params {:x 1, :y "2"}})
; {:status 400,
;  :body {:type :reitit.ring.coercion/request-coercion
;         :coercion :schema
;         :in [:request :body-params]
;         :value {:x 1, :y "2"}
;         :schema {:x "Int", :y "Int"}
;         :errors {:y "(not (integer? \"2\"))"}}}
```

### Example with data-specs

```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.coercion :as coercion])
(require '[reitit.ring.coercion.spec :as spec])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/ping" {:post {:parameters {:body {:x int?, :y int?}}
                        :responses {200 {:schema {:total pos-int?}}}
                        :handler (fn [{{{:keys [x y]} :body} :parameters}]
                                   {:status 200
                                    :body {:total (+ x y)}})}}]]
      {:data {:middleware [coercion/gen-wrap-coerce-exceptions
                           coercion/gen-wrap-coerce-parameters
                           coercion/gen-wrap-coerce-response]
              :coercion spec/coercion}})))
```

Valid request:

```clj
(app
  {:request-method :post
   :uri "/api/ping"
   :body-params {:x 1, :y 2}})
; {:status 200
;  :body {:total 3}}
```

Invalid request:

```clj
(app
  {:request-method :post
   :uri "/api/ping"
   :body-params {:x 1, :y "2"}})
; {:status 400,
;  :body {:type ::coercion/request-coercion
;         :coercion :spec
;         :in [:request :body-params]
;         :value {:x 1, :y "2"}
;         :spec "(spec-tools.core/spec {:spec (clojure.spec.alpha/keys :req-un [:$spec37747/x :$spec37747/y]), :type :map, :keys #{:y :x}, :keys/req #{:y :x}})"
;         :problems [{:path [:y]
;                     :pred "clojure.core/int?"
;                     :val "2"
;                     :via [:$spec37747/y]
;                     :in [:y]}]}}
```

### Example with clojure.spec

Currently, `clojure.spec` [doesn't support runtime transformations via conforming](https://dev.clojure.org/jira/browse/CLJ-2116), so one needs to wrap all specs with `spec-tools.core/spec`.

```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.coercion :as coercion])
(require '[reitit.ring.coercion.spec :as spec])
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
       ["/ping" {:post {:parameters {:body ::request}
                        :responses {200 {:schema ::response}}
                        :handler (fn [{{{:keys [x y]} :body} :parameters}]
                                   {:status 200
                                    :body {:total (+ x y)}})}}]]
      {:data {:middleware [coercion/gen-wrap-coerce-exceptions
                           coercion/gen-wrap-coerce-parameters
                           coercion/gen-wrap-coerce-response]
              :coercion spec/coercion}})))
```

Valid request:

```clj
(app
  {:request-method :post
   :uri "/api/ping"
   :body-params {:x 1, :y 2}})
; {:status 200
;  :body {:total 3}}
```

Invalid request:

```clj
(app
  {:request-method :post
   :uri "/api/ping"
   :body-params {:x 1, :y "2"}})
; {:status 400,
;  :body {:type ::coercion/request-coercion
;         :coercion :spec
;         :in [:request :body-params]
;         :value {:x 1, :y "2"}
;         :spec "(spec-tools.core/spec {:spec (clojure.spec.alpha/keys :req-un [:reitit.coercion-test/x :reitit.coercion-test/y]), :type :map, :keys #{:y :x}, :keys/req #{:y :x}})"
;         :problems [{:path [:y]
;                     :pred "clojure.core/int?"
;                     :val "2"
;                     :via [::request ::y]
;                     :in [:y]}]}}
```

### Custom coercion

Both Schema and Spec Coercion can be configured via options, see the source code for details.

To plug in new validation engine, see the
`reitit.ring.coercion.protocol/Coercion` protocol.

```clj
(defprotocol Coercion
  "Pluggable coercion protocol"
  (get-name [this] "Keyword name for the coercion")
  (compile [this model name] "Compiles a coercion model")
  (get-apidocs [this model data] "???")
  (make-open [this model] "Returns a new map model which doesn't fail on extra keys")
  (encode-error [this error] "Converts error in to a serializable format")
  (request-coercer [this type model] "Returns a `value format => value` request coercion function")
  (response-coercer [this model] "Returns a `value format => value` response coercion function"))
```
