# reitit [![Build Status](https://travis-ci.org/metosin/reitit.svg?branch=master)](https://travis-ci.org/metosin/reitit) [![Dependencies Status](https://jarkeeper.com/metosin/reitit/status.svg)](https://jarkeeper.com/metosin/reitit)

Snappy data-driven router for Clojure(Script).

* Simple data-driven route syntax
* Generic, not tied to HTTP
* Extendable
* Fast

## Latest version

[![Clojars Project](http://clojars.org/metosin/reitit/latest-version.svg)](http://clojars.org/metosin/reitit)

## Usage

Named routes (example from [bide](https://github.com/funcool/bide#why-another-routing-library)).

```clj
(require '[reitit.core :as reitit])

(def router
  (reitit/router
    [["/auth/login" :auth/login]
     ["/auth/recovery/token/:token" :auth/recovery]
     ["/workspace/:project-uuid/:page-uuid" :workspace/page]]))

(reitit/match-route router "/workspace/1/2")
; {:name :workspace/page
;  :route-params {:project-uuid "1", :page-uuid "2"}}
```

Nested routes with meta-data:

```clj
(def handler (constantly "ok"))

(def ring-router
  (reitit/router
    ["/api" {:middleware [:api]}
     ["/ping" handler]
     ["/public/*path" handler]
     ["/user/:id" {:parameters {:id String}
                   :handler handler}]
     ["/admin" {:middleware [:admin] :roles #{:admin}}
      ["/root" {:roles ^:replace #{:root}
                :handler handler}]
      ["/db" {:middleware [:db]
              :handler handler}]]]))

(reitit/match-route ring-router "/api/admin/db")
; {:middleware [:api :admin :db]
;  :roles #{:admin}
;  :handler #object[...]
;  :route-params {}}
```

## Special thanks

To all Clojure(Script) routing libs out there, expecially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [Compojure](https://github.com/weavejester/compojure) and
[Pedestal Route](https://github.com/pedestal/pedestal/tree/master/route),

## License

Copyright © 2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
