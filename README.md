# reitit [![Build Status](https://travis-ci.org/metosin/reitit.svg?branch=master)](https://travis-ci.org/metosin/reitit)

A friendly data-driven router for Clojure(Script).

* Simple data-driven [route syntax](https://metosin.github.io/reitit/basics/route_syntax.html)
* Route [conflict resolution](https://metosin.github.io/reitit/basics/route_conflicts.html)
* First-class [route data](https://metosin.github.io/reitit/basics/route_data.html)
* Bi-directional routing
* [Pluggable coercion](https://metosin.github.io/reitit/coercion/coercion.html) ([schema](https://github.com/plumatic/schema) & [clojure.spec](https://clojure.org/about/spec))
* Extendable
* Modular
* [Fast](https://metosin.github.io/reitit/performance.html)

There are also a separate [Ring-router](https://metosin.github.io/reitit/ring/ring.html) module with [data-driven middleware](https://metosin.github.io/reitit/ring/data_driven_middleware.html).

See the [full documentation](https://metosin.github.io/reitit/) for details.

## Latest version

All bundled:

```clj
[metosin/reitit "0.1.0-SNAPSHOT"]
```

Optionally, the parts can be required separately:

```clj
[metosin/reitit-core "0.1.0-SNAPSHOT"] ; just the router
[metosin/reitit-ring "0.1.0-SNAPSHOT"] ; ring-router
[metosin/reitit-spec "0.1.0-SNAPSHOT"] ; spec coercion
[metosin/reitit-schema "0.1.0-SNAPSHOT"] ; schema coercion
```

## Quick start

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/api/ping" ::ping]
     ["/api/orders/:id" ::order]]))

(r/match-by-path router "/api/ping")
; #Match{:template "/api/ping"
;        :data {:name ::ping}
;        :result nil
;        :params {}
;        :path "/api/ping"}

(r/match-by-name router ::order {:id 2})
; #Match{:template "/api/orders/:id",
;        :data {:name ::order},
;        :result nil,
;        :params {:id 2},
;        :path "/api/orders/2"}
```

## Ring example

A Ring routing app with input & output coercion using [data-specs](https://github.com/metosin/spec-tools/blob/master/README.md#data-specs).

```clj
(require '[reitit.ring :as ring])
(require '[reitit.coercion.spec])
(require '[reitit.ring.coercion-middleware :as mw])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/math" {:get {:coercion reitit.coercion.spec/coercion
                       :parameters {:query {:x int?, :y int?}}
                       :responses {200 {:schema {:total pos-int?}}}
                       :handler (fn [{{{:keys [x y]} :query} :parameters}]
                                  {:status 200
                                   :body {:total (+ x y)}})}}]]
      {:data {:middleware [mw/coerce-exceptions-middleware
                           mw/coerce-request-middleware
                           mw/coerce-response-middleware]}})))
```

Valid request:

```clj
(app {:request-method :get
      :uri "/api/math"
      :query-params {:x "1", :y "2"}})
; {:status 200
;  :body {:total 3}}
```

Invalid request:

```clj
(app {:request-method :get
      :uri "/api/math"
      :query-params {:x "1", :y "a"}})
;{:status 400,
; :body {:type :reitit.coercion/request-coercion,
;        :coercion :spec,
;        :spec "(spec-tools.core/spec {:spec (clojure.spec.alpha/keys :req-un [:$spec20745/x :$spec20745/y]), :type :map, :keys #{:y :x}, :keys/req #{:y :x}})",
;        :problems [{:path [:y],
;                    :pred "clojure.core/int?",
;                    :val "a",
;                    :via [:$spec20745/y],
;                    :in [:y]}],
;        :value {:x "1", :y "a"},
;        :in [:request :query-params]}}


```

## More info

[Check out the full documentation!](https://metosin.github.io/reitit/)

Join [#reitit](https://clojurians.slack.com/messages/reitit/) channel in [Clojurians slack](http://clojurians.net/).

Roadmap is mostly written in [issues](https://github.com/metosin/reitit/issues).

## Special thanks

* Existing Clojure(Script) routing libs, expecially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [Compojure](https://github.com/weavejester/compojure) and
[Pedestal](https://github.com/pedestal/pedestal/tree/master/route).
* [Compojure-api](https://github.com/metosin/compojure-api), [Kekkonen](https://github.com/metosin/kekkonen), [Ring-swagger](https://github.com/metosin/ring-swagger) and [Yada](https://github.com/juxt/yada) and for ideas, coercion & stuff.
* [Schema](https://github.com/plumatic/schema) and [clojure.spec](https://clojure.org/about/spec) for the formal validation.

## Development instructions

The documentation is built with [gitbook](https://toolchain.gitbook.com). To preview your changes locally:

```bash
npm install -g gitbook-cli
gitbook install
gitbook serve
```

To bump up version:

```bash
# new version
./scripts/set-version "1.0.0"
./scripts/lein-modules install

# works
lein test

# deploy to clojars
./scripts/lein-modules do clean, deploy clojars
```

## License

Copyright © 2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
