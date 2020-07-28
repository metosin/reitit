# reitit [![Build Status](https://img.shields.io/circleci/project/github/metosin/reitit.svg)](https://circleci.com/gh/metosin/reitit) [![cljdoc badge](https://cljdoc.org/badge/metosin/reitit)](https://cljdoc.org/jump/release/metosin/reitit) [![Slack](https://img.shields.io/badge/clojurians-reitit-blue.svg?logo=slack)](https://clojurians.slack.com/messages/reitit/)

A fast data-driven router for Clojure(Script).

* Simple data-driven [route syntax](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-syntax/)
* Route [conflict resolution](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-conflicts/)
* First-class [route data](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-data/)
* Bi-directional routing
* [Pluggable coercion](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/coercion/coercion/) ([malli](https://github.com/metosin/malli), [schema](https://github.com/plumatic/schema) & [clojure.spec](https://clojure.org/about/spec))
* Helpers for [ring](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/ring/ring/), [http](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/http/interceptors/), [pedestal](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/http/pedestal/) & [frontend](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/frontend/basics/)
* Friendly [Error Messages](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/error-messages/)
* Extendable
* Modular
* [Fast](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/performance/)

Presentations:
* [Reitit, The Ancient Art of Data-Driven](https://www.slideshare.net/mobile/metosin/reitit-clojurenorth-2019-141438093), Clojure/North 2019, [video](https://youtu.be/cSntRGAjPiM)
* [Faster and Friendlier Routing with Reitit 0.3.0](https://www.metosin.fi/blog/faster-and-friendlier-routing-with-reitit030/)
* [Welcome Reitit 0.2.0!](https://www.metosin.fi/blog/reitit020/)
* [Data-Driven Ring with Reitit](https://www.metosin.fi/blog/reitit-ring/)
* [Reitit, Data-Driven Routing with Clojure(Script)](https://www.metosin.fi/blog/reitit/)

## [Full Documentation](https://cljdoc.org/d/metosin/reitit/CURRENT)

There is [#reitit](https://clojurians.slack.com/messages/reitit/) in [Clojurians Slack](http://clojurians.net/) for discussion & help.

## Main Modules

* `reitit` - all bundled
* `reitit-core` - the routing core
* `reitit-ring` - a [ring router](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/ring/ring/)
* `reitit-middleware` - [common middleware](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/ring/default-middleware/)
* `reitit-spec` [clojure.spec](https://clojure.org/about/spec) coercion
* `reitit-malli` [malli](https://github.com/metosin/malli) coercion
* `reitit-schema` [Schema](https://github.com/plumatic/schema) coercion
* `reitit-swagger` [Swagger2](https://swagger.io/) apidocs
* `reitit-swagger-ui` Integrated [Swagger UI](https://github.com/swagger-api/swagger-ui)
* `reitit-frontend` Tools for [frontend routing]((https://cljdoc.org/d/metosin/reitit/CURRENT/doc/frontend/basics/))
* `reitit-http` http-routing with Interceptors
* `reitit-interceptors` - [common interceptors](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/http/default-interceptors/)
* `reitit-sieppari` support for [Sieppari](https://github.com/metosin/sieppari)
* `reitit-dev` - development utilities

## Extra modules

* `reitit-pedestal` support for [Pedestal](http://pedestal.io)

## Latest version

All main modules bundled:

```clj
[metosin/reitit "0.5.5"]
```

Optionally, the parts can be required separately.

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

## More examples

* [`reitit-ring` with coercion, swagger and default middleware](https://github.com/metosin/reitit/blob/master/examples/ring-swagger/src/example/server.clj)
* [`reitit-frontend`, the easy way](https://github.com/metosin/reitit/blob/master/examples/frontend/src/frontend/core.cljs)
* [`reitit-frontend` with Keechma-style controllers](https://github.com/metosin/reitit/blob/master/examples/frontend-controllers/src/frontend/core.cljs)
* [`reitit-http` with Pedestal](https://github.com/metosin/reitit/blob/master/examples/pedestal/src/example/server.clj)
* [`reitit-http` with Sieppari](https://github.com/metosin/reitit/blob/master/examples/http/src/example/server.clj)

All examples are in https://github.com/metosin/reitit/tree/master/examples

## More info

[Check out the full documentation!](https://cljdoc.org/d/metosin/reitit/CURRENT/)

Join [#reitit](https://clojurians.slack.com/messages/reitit/) channel in [Clojurians slack](http://clojurians.net/).

Roadmap is mostly written in [issues](https://github.com/metosin/reitit/issues).

## Special thanks

* Existing Clojure(Script) routing libs, especially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [calfpath](https://github.com/ikitommi/calfpath), [Compojure](https://github.com/weavejester/compojure), [Keechma](https://keechma.com/) and
[Pedestal](https://github.com/pedestal/pedestal/tree/master/route).
* [Compojure-api](https://github.com/metosin/compojure-api), [Kekkonen](https://github.com/metosin/kekkonen), [Ring-swagger](https://github.com/metosin/ring-swagger) and [Yada](https://github.com/juxt/yada) and for ideas, coercion & stuff.
* [Schema](https://github.com/plumatic/schema) and [clojure.spec](https://clojure.org/about/spec) for the validation part.
* [httprouter](https://github.com/julienschmidt/httprouter) for ideas and a good library to benchmark against

## License

Copyright © 2017-2020 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
