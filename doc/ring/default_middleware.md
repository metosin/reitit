# Default Middleware

```clj
[metosin/reitit-middleware "0.5.5"]
```

Any Ring middleware can be used with `reitit-ring`, but using data-driven middleware is preferred as they are easier to manage and in many cases, yield better performance. `reitit-middleware` contains a set of common ring middleware, lifted into data-driven middleware.

* [Parameter Handling](#parameters-handling)
* [Exception Handling](#exception-handling)
* [Content Negotiation](#content-negotiation)
* [Multipart Request Handling](#multipart-request-handling)
* [Inspecting Middleware Chain](#inspecting-middleware-chain)

## Parameters Handling

`reitit.ring.middleware.parameters/parameters-middleware` to capture query- and form-params. Wraps
`ring.middleware.params/wrap-params`.

**NOTE**: will be factored into two parts: a query-parameters middleware and a Muuntaja format responsible for the the `application/x-www-form-urlencoded` body format.

## Exception Handling

See [Exception Handling with Ring](exceptions.md).

## Content Negotiation

See [Content Negotiation](content_negotiation.md).

## Multipart Request Handling

Wrapper for [Ring Multipart Middleware](https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/multipart_params.clj). Emits swagger `:consumes` definitions automatically.

Expected route data:
 
| key          | description |
| -------------|-------------|
| `[:parameters :multipart]`  | mounts only if defined for a route.


```clj
(require '[reitit.ring.middleware.multipart :as multipart])
```

* `multipart/multipart-middleware` a preconfigured middleware for multipart handling
* `multipart/create-multipart-middleware` to generate with custom configuration

## Inspecting Middleware Chain

`reitit.ring.middleware.dev/print-request-diffs` is a [middleware chain transforming function](transforming_middleware_chain.md). It prints a request and response diff between each middleware. To use it, add the following router option:

```clj
:reitit.middleware/transform reitit.ring.middleware.dev/print-request-diffs
```

Partial sample output:

![Opensensors perf test](../images/ring-request-diff.png)

## Example app

See an example app with the default middleware in action: https://github.com/metosin/reitit/blob/master/examples/ring-swagger/src/example/server.clj.
