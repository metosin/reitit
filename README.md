# reitit

[![Build Status](https://github.com/metosin/reitit/actions/workflows/testsuite.yml/badge.svg)](https://github.com/metosin/reitit/actions)
[![cljdoc badge](https://cljdoc.org/badge/metosin/reitit)](https://cljdoc.org/d/metosin/reitit/)
[![Clojars Project](https://img.shields.io/clojars/v/metosin/reitit.svg)](https://clojars.org/metosin/reitit)
[![Slack](https://img.shields.io/badge/clojurians-reitit-blue.svg?logo=slack)](https://clojurians.slack.com/messages/reitit/)

<img src="https://github.com/metosin/reitit/blob/master/doc/images/reitit.png?raw=true" align="right" width="200" />
A fast data-driven router for Clojure(Script).

* Simple data-driven [route syntax](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-syntax/)
* Route [conflict resolution](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-conflicts/)
* First-class [route data](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-data/)
* Bi-directional routing
* [Pluggable coercion](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/coercion/coercion-explained) ([malli](https://github.com/metosin/malli), [schema](https://github.com/plumatic/schema) & [clojure.spec](https://clojure.org/about/spec))
* Helpers for [ring](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/ring/ring-router), [http](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/http/interceptors/), [pedestal](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/http/pedestal/) & [frontend](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/frontend/basics/)
* Friendly [Error Messages](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/error-messages/)
* Extendable
* Modular
* [Fast](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/misc/performance)

Presentations:
* [Reitit, The Ancient Art of Data-Driven](https://www.slideshare.net/mobile/metosin/reitit-clojurenorth-2019-141438093), Clojure/North 2019, [video](https://youtu.be/cSntRGAjPiM)
* [Faster and Friendlier Routing with Reitit 0.3.0](https://www.metosin.fi/blog/faster-and-friendlier-routing-with-reitit030/)
* [Welcome Reitit 0.2.0!](https://www.metosin.fi/blog/reitit020/)
* [Data-Driven Ring with Reitit](https://www.metosin.fi/blog/reitit-ring/)
* [Reitit, Data-Driven Routing with Clojure(Script)](https://www.metosin.fi/blog/reitit/)

**Status:** [stable](https://github.com/metosin/open-source#project-lifecycle-model)

> Hi! We are [Metosin](https://metosin.fi), a consulting company. These libraries have evolved out of the work we do for our clients.
> We maintain & develop this project, for you, for free. Issues and pull requests welcome!
> However, if you want more help using the libraries, or want us to build something as cool for you, consider our [commercial support](https://www.metosin.fi/en/open-source-support).

## [Full Documentation](https://cljdoc.org/d/metosin/reitit/CURRENT)

There is [#reitit](https://clojurians.slack.com/messages/reitit/) in [Clojurians Slack](http://clojurians.net/) for discussion & help.

## Main Modules

* `metosin/reitit` - all bundled
* `metosin/reitit-core` - the routing core
* `metosin/reitit-ring` - a [ring router](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/ring/ring-router/)
* `metosin/reitit-middleware` - [common middleware](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/ring/default-middleware/)
* `metosin/reitit-spec` [clojure.spec](https://clojure.org/about/spec) coercion
* `metosin/reitit-malli` [malli](https://github.com/metosin/malli) coercion
* `metosin/reitit-schema` [Schema](https://github.com/plumatic/schema) coercion
* `fi.metosin/reitit-openapi` [OpenAPI](https://www.openapis.org/) apidocs *
* `metosin/reitit-swagger` [Swagger2](https://swagger.io/) apidocs
* `metosin/reitit-swagger-ui` Integrated [Swagger UI](https://github.com/swagger-api/swagger-ui)
* `metosin/reitit-frontend` Tools for [frontend routing]((https://cljdoc.org/d/metosin/reitit/CURRENT/doc/frontend/basics/))
* `metosin/reitit-http` http-routing with Interceptors
* `metosin/reitit-interceptors` - [common interceptors](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/http/default-interceptors/)
* `metosin/reitit-sieppari` support for [Sieppari](https://github.com/metosin/sieppari)
* `metosin/reitit-dev` - development utilities

... * This is not a typo; the new `reitit-openapi` was released under the new, verified `fi.metosin` group. Existing
modules will continue to be released under `metosin` for compatibility purposes.

## Extra modules

* `reitit-pedestal` support for [Pedestal](http://pedestal.io)

## Latest version

All main modules bundled:

```clj
[metosin/reitit "0.9.1"]
```

Optionally, the parts can be required separately.

Reitit requires Clojure 1.11 and Java 11.

Reitit is tested with the LTS releases Java 11, 17 and 21.

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
(require '[muuntaja.core :as m])
(require '[reitit.ring :as ring])
(require '[reitit.coercion.spec])
(require '[reitit.ring.coercion :as rrc])
(require '[reitit.ring.middleware.exception :as exception])
(require '[reitit.ring.middleware.muuntaja :as muuntaja])
(require '[reitit.ring.middleware.parameters :as parameters])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/math" {:get {:parameters {:query {:x int?, :y int?}}
                       :responses  {200 {:body {:total int?}}}
                       :handler    (fn [{{{:keys [x y]} :query} :parameters}]
                                     {:status 200
                                      :body   {:total (+ x y)}})}}]]
      ;; router data affecting all routes
      {:data {:coercion   reitit.coercion.spec/coercion
              :muuntaja   m/instance
              :middleware [parameters/parameters-middleware ; decoding query & form params
                           muuntaja/format-middleware       ; content negotiation
                           exception/exception-middleware   ; converting exceptions to HTTP responses
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))
```

Valid request:

```clj
(-> (app {:request-method :get
          :uri "/api/math"
          :query-params {:x "1", :y "2"}})
    (update :body slurp))
; {:status 200
;  :body "{\"total\":3}"
;  :headers {"Content-Type" "application/json; charset=utf-8"}}
```

Invalid request:

```clj
(-> (app {:request-method :get
          :uri "/api/math"
          :query-params {:x "1", :y "a"}})
    (update :body jsonista.core/read-value))
; {:status 400
;  :headers {"Content-Type" "application/json; charset=utf-8"}
;  :body {"spec" "(spec-tools.core/spec {:spec (clojure.spec.alpha/keys :req-un [:spec$8974/x :spec$8974/y]), :type :map, :leaf? false})"
;         "value" {"x" "1"
;                  "y" "a"}
;         "problems" [{"via" ["spec$8974/y"]
;                      "path" ["y"]
;                      "pred" "clojure.core/int?"
;                      "in" ["y"]
;                      "val" "a"}]
;         "type" "reitit.coercion/request-coercion"
;         "coercion" "spec"
;         "in" ["request" "query-params"]}}
```

## More examples

* [`reitit-ring` with coercion, swagger and default middleware](https://github.com/metosin/reitit/blob/master/examples/ring-malli-swagger/src/example/server.clj)
* [`reitit-frontend`, the easy way](https://github.com/metosin/reitit/blob/master/examples/frontend/src/frontend/core.cljs)
* [`reitit-frontend` with Keechma-style controllers](https://github.com/metosin/reitit/blob/master/examples/frontend-controllers/src/frontend/core.cljs)
* [`reitit-http` with Pedestal](https://github.com/metosin/reitit/blob/master/examples/pedestal/src/example/server.clj)
* [`reitit-http` with Sieppari](https://github.com/metosin/reitit/blob/master/examples/http/src/example/server.clj)

All examples are in https://github.com/metosin/reitit/tree/master/examples

## External resources
* Simple web application using Ring/Reitit and Integrant: https://github.com/PrestanceDesign/usermanager-reitit-integrant-example
* A simple Clojure backend using Reitit to serve up a RESTful API: [startrek](https://github.com/dharrigan/startrek). Technologies include:
    * [Donut System](https://github.com/donut-party/system)
    * [next-jdbc](https://github.com/seancorfield/next-jdbc)
    * [JUXT Clip](https://github.com/juxt/clip)
    * [Flyway](https://github.com/flyway/flyway)
    * [HoneySQL](https://github.com/seancorfield/honeysql)
    * [Babashka](https://babashka.org)
* https://www.learnreitit.com/
* Lipas, liikuntapalvelut: https://github.com/lipas-liikuntapaikat/lipas
* Implementation of the Todo-Backend API spec, using Clojure, Ring/Reitit and next-jdbc: https://github.com/PrestanceDesign/todo-backend-clojure-reitit
* Ping CRM, a single page app written in Clojure Ring, Reitit, Integrant and next.jdbc: https://github.com/prestancedesign/clojure-inertia-pingcrm-demo

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

Copyright © 2017-2023 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
