# reitit [![Build Status](https://img.shields.io/circleci/project/github/metosin/reitit.svg)](https://circleci.com/gh/metosin/reitit)

A fast data-driven router for Clojure(Script).

* Simple data-driven [route syntax](https://metosin.github.io/reitit/basics/route_syntax.html)
* Route [conflict resolution](https://metosin.github.io/reitit/basics/route_conflicts.html)
* First-class [route data](https://metosin.github.io/reitit/basics/route_data.html)
* Bi-directional routing
* [Pluggable coercion](https://metosin.github.io/reitit/coercion/coercion.html) ([schema](https://github.com/plumatic/schema) & [clojure.spec](https://clojure.org/about/spec))
* Extendable
* Modular
* [Fast](https://metosin.github.io/reitit/performance.html)

Posts:
* [Reitit, Data-Driven Routing with Clojure(Script)](https://www.metosin.fi/blog/reitit/)
* [Data-Driven Ring with Reitit](https://www.metosin.fi/blog/reitit-ring/)

See the [full documentation](https://metosin.github.io/reitit/) for details.

## Modules

* `reitit-core` - the routing core
* `reitit-ring` - a [ring router](https://metosin.github.io/reitit/ring/ring.html)
* `reitit-middleware` - [common data-driven middleware](https://metosin.github.io/reitit/ring/default_middleware.html) for `reitit-ring`
* `reitit-spec` [clojure.spec](https://clojure.org/about/spec) coercion
* `reitit-schema` [Schema](https://github.com/plumatic/schema) coercion
* `reitit-swagger` [Swagger2](https://swagger.io/) apidocs
* `reitit-swagger-ui` Integrated [Swagger UI](https://github.com/swagger-api/swagger-ui)

Bubblin' under:

* `reitit-http` with enchanced Pedestal-style Interceptors (WIP)
* `reitit-frontend` with Keechma-style Controllers (WIP)

## Latest version

All bundled:

```clj
[metosin/reitit "0.2.0-SNAPSHOT"]
```

Optionally, the parts can be required separately:

```clj
[metosin/reitit-core "0.2.0-SNAPSHOT"]
[metosin/reitit-ring "0.2.0-SNAPSHOT"]
[metosin/reitit-spec "0.2.0-SNAPSHOT"]
[metosin/reitit-schema "0.2.0-SNAPSHOT"]
[metosin/reitit-swagger "0.2.0-SNAPSHOT"]
[metosin/reitit-swagger-ui "0.2.0-SNAPSHOT"]
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
;        :path-params {}
;        :path "/api/ping"}

(r/match-by-name router ::order {:id 2})
; #Match{:template "/api/orders/:id",
;        :data {:name ::order},
;        :result nil,
;        :path-params {:id 2},
;        :path "/api/orders/2"}
```

## Ring example

A Ring routing app with input & output coercion using [data-specs](https://github.com/metosin/spec-tools/blob/master/README.md#data-specs).

```clj
(require '[reitit.ring :as ring])
(require '[reitit.coercion.spec])
(require '[reitit.ring.coercion :as rrc])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/math" {:get {:parameters {:query {:x int?, :y int?}}
                       :responses {200 {:body {:total pos-int?}}}
                       :handler (fn [{{{:keys [x y]} :query} :parameters}]
                                  {:status 200
                                   :body {:total (+ x y)}})}}]]
      ;; router data effecting all routes
      {:data {:coercion reitit.coercion.spec/coercion
              :middleware [rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))
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

**NOTE**: Reitit is not a batteries included web-stack. You should also include at least:
* content negotiation library like [Muuntaja](https://github.com/metosin/muuntaja)
* some default Ring-middleware like `ring.middleware.params/wrap-params`

See [Ring with Swagger Example](https://github.com/metosin/reitit/tree/master/examples/ring-swagger) for a runnable example. Plan is to have more batteries as separate modules, templates and example projects. Stay tuned.

## More info

[Check out the full documentation!](https://metosin.github.io/reitit/)

Join [#reitit](https://clojurians.slack.com/messages/reitit/) channel in [Clojurians slack](http://clojurians.net/).

Roadmap is mostly written in [issues](https://github.com/metosin/reitit/issues).

## Special thanks

* Existing Clojure(Script) routing libs, expecially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [Compojure](https://github.com/weavejester/compojure) and
[Pedestal](https://github.com/pedestal/pedestal/tree/master/route).
* [Compojure-api](https://github.com/metosin/compojure-api), [Kekkonen](https://github.com/metosin/kekkonen), [Ring-swagger](https://github.com/metosin/ring-swagger) and [Yada](https://github.com/juxt/yada) and for ideas, coercion & stuff.
* [Schema](https://github.com/plumatic/schema) and [clojure.spec](https://clojure.org/about/spec) for the validation part.

## License

Copyright © 2017-2018 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
