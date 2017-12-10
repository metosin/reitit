# Coercion

Coercion is a process of transforming parameters from one format into another.

By default, all wildcard and catch-all parameters are parsed as Strings:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    ["/:company/users/:user-id" ::user-view]))
```

Here's a match:

```clj
(r/match-by-path r "/metosin/users/123")
; #Match{:template "/:company/users/:user-id",
;        :data {:name :user/user-view},
;        :result nil,
;        :params {:company "metosin", :user-id "123"},
;        :path "/metosin/users/123"}
```

To enable parameter coercion, we need to do few things:

1. Choose a `Coercion` for the routes
2. Defined types for the parameters
3. Compile coercers for the types
4. Apply the coercion

## Coercion

`Coercion` is a protocol defining how types can be defined, coerced and inventoried.

Reitit ships with the following coercion modules:

* `reitit.coercion.schema/coercion` for [plumatic schema](https://github.com/plumatic/schema).
* `reitit.coercion.spec/coercion` for both [clojure.spec](https://clojure.org/about/spec) and [data-specs](https://github.com/metosin/spec-tools#data-specs).

Coercion can be attached to routes using a `:coercion` key in the route data. There can be multiple `Coercion` implementation into a single router, normal [scoping rules](../basics/route_data.html#nested-route-data) apply here too.

## Defining types for parameters

Route parameters can be defined via route data `:parameters`. It can be submaps to define different types of parameters: `:query`, `:body`, `:form`, `:header` and `:path`. Syntax for the actual parameters is defined by the `Coercion` being used.

#### Schema

```clj
(require '[reitit.coercion.schema])
(require '[schema.core :as schema])

(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:company schema/Str
                                                     :user-id schema/Int}]))
```

#### Clojure.spec

Currently, `clojure.spec` [doesn't support runtime transformations via conforming](https://dev.clojure.org/jira/browse/CLJ-2116), so one needs to wrap all specs with `spec-tools.core/spec` to get this working.


```clj
(require '[reitit.coercion.spec])
(require '[spec-tools.spec :as spec])
(require '[clojure.spec :as s])

(s/def ::company spec/string?
(s/def ::user-id spec/int?
(s/def ::path-params (s/keys :req-un [::company ::user-id]))

(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.spec/coercion
                                 :parameters {:path ::path-params]))
```

#### Data-specs

```clj
(require '[reitit.coercion.spec])

(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.spec/coercion
                                 :parameters {:path {:company str?
                                                     :user-id int?}]))
```

So, now we have our


### Thanks to

Most of the thing are just redefined version of the original implementation. Big thanks to:

* [compojure-api](https://clojars.org/metosin/compojure-api) for the initial `Coercion` protocol
* [ring-swagger](https://github.com/metosin/ring-swagger#more-complete-example) for the syntax for the `:paramters` (and `:responses`).
