# Coercion Explained

Coercion is a process of transforming parameters (and responses) from one format into another. Reitit separates routing and coercion into separate steps.

By default, all wildcard and catch-all parameters are parsed as Strings:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    ["/:company/users/:user-id" ::user-view]))
```

Here's a match with the String `:params`:

```clj
(r/match-by-path r "/metosin/users/123")
; #Match{:template "/:company/users/:user-id",
;        :data {:name :user/user-view},
;        :result nil,
;        :params {:company "metosin", :user-id "123"},
;        :path "/metosin/users/123"}
```

To enable parameter coercion, we need to do few things:

1. Define a `Coercion` for the routes
2. Define types for the parameters
3. Compile coercers for the types
4. Apply the coercion

## Define Coercion

`reitit.coercion/Coercion` is a protocol defining how types are defined, coerced and inventoried.

Reitit has the following coercion modules:

* `reitit.coercion.schema/coercion` for [plumatic schema](https://github.com/plumatic/schema).
* `reitit.coercion.spec/coercion` for both [clojure.spec](https://clojure.org/about/spec) and [data-specs](https://github.com/metosin/spec-tools#data-specs).

Coercion can be attached to route data under `:coercion` key. There can be multiple `Coercion` implementation into a single router, normal [scoping rules](../basics/route_data.html#nested-route-data) apply.

## Defining parameters

Route parameters can be defined via route data `:parameters`. It has keys for different type of parameters: `:query`, `:body`, `:form`, `:header` and `:path`. Syntax for the actual parameters is defined by the `Coercion`.

Here's the example with Schema path-parameters:

```clj
(require '[reitit.coercion.schema])
(require '[schema.core :as s])

(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:company s/Str
                                                     :user-id s/Int}}}]))
```

Routing again:

```clj
(r/match-by-path r "/metosin/users/123")
; #Match{:template "/:company/users/:user-id",
;        :data {:name :user/user-view,
;               :coercion #SchemaCoercion{...}
;               :parameters {:path {:company java.lang.String,
;                                   :user-id Int}}},
;        :result nil,
;        :params {:company "metosin", :user-id "123"},
;        :path "/metosin/users/123"}
```

Coercion was not applied. Why? All we did was just added more data to the route and the routing functions are just responsible for routing, not coercion.

But now we should have enough data on the match to apply the coercion.

## Compiling coercers

Before the actual coercion, we need to compile the coercers against the route data. This is because compiled coercers yield much better performance. A separate step makes thing explicit and non-magical. Compiling could be done via a Middleware, Interceptor but we can also do it at Router-level, effecting all routes.

There is a helper function for the coercer compiling in `reitit.coercion`:

```clj
(require '[reitit.coercion :as coercion])
(require '[reitit.coercion.schema])
(require '[schema.core :as s])

(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:company s/Str
                                                     :user-id s/Int}}}]
    {:compile coercion/compile-request-coercers}))
```

Routing again:

```clj
(r/match-by-path r "/metosin/users/123")
; #Match{:template "/:company/users/:user-id",
;        :data {:name :user/user-view,
;               :coercion #SchemaCoercion{...}
;               :parameters {:path {:company java.lang.String,
;                                   :user-id Int}}},
;        :result {:path #object[reitit.coercion$request_coercer$]},
;        :params {:company "metosin", :user-id "123"},
;        :path "/metosin/users/123"}
```

The compiler added a `:result` key into the match (done just once, at router creation time), which holds the compiled coercers. We are almost done.

## Applying coercion

We can use a helper function to do the actual coercion, based on a `Match`:

```clj
(coercion/coerce!
  (r/match-by-path router "/metosin/users/123"))
; {:path {:company "metosin", :user-id 123}}
```

If a coercion fails, a typed (`:reitit.coercion/request-coercion`) ExceptionInfo is thrown, with descriptive data about the actual error:

```clj
(coercion/coerce!
  (r/match-by-path router "/metosin/users/ikitommi"))
; => ExceptionInfo Request coercion failed:
; #CoercionError{:schema {:company java.lang.String, :user-id Int, Any Any},
;                :errors {:user-id (not (integer? "ikitommi"))}}
; clojure.core/ex-info (core.clj:4739)
```

## Full example

Here's an full example of routing + coercion.

```clj
(require '[reitit.coercion.schema])
(require '[reitit.coercion :as coercion])
(require '[schema.core :as s])

(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:company s/Str
                                                     :user-id s/Int}}}]
    {:compile coercion/compile-request-coercers}))

(defn route-and-coerce! [path]
  (if-let [match (r/match-by-path router path)]
    (assoc match :parameters (coercion/coerce! match))))

(route-and-coerce! "/metosin/users/123")
; #Match{:template "/:company/users/:user-id",
;        :data {:name :user/user-view,
;               :coercion #SchemaCoercion{...}
;               :parameters {:path {:company java.lang.String,
;                                   :user-id Int}}},
;        :result {:path #object[reitit.coercion$request_coercer$]},
;        :params {:company "metosin", :user-id "123"},
;        :parameters {:path {:company "metosin", :user-id 123}}
;        :path "/metosin/users/123"}

(route-and-coerce! "/metosin/users/ikitommi")
; => ExceptionInfo Request coercion failed...
```

## Ring Coercion

For a full-blown http-coercion, see the [ring coercion](../ring/coercion.md).

## Thanks to

Most of the thing are just polished version of the original implementations. Big thanks to:

* [compojure-api](https://clojars.org/metosin/compojure-api) for the initial `Coercion` protocol
* [ring-swagger](https://github.com/metosin/ring-swagger#more-complete-example) for the syntax of the `:paramters` (and `:responses`).
