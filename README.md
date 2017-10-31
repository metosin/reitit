# reitit [![Build Status](https://travis-ci.org/metosin/reitit.svg?branch=master)](https://travis-ci.org/metosin/reitit)

A friendly data-driven router for Clojure(Script).

* Simple data-driven [route syntax](https://metosin.github.io/reitit/basics/route_syntax.html)
* Route [conflict resolution](https://metosin.github.io/reitit/basics/route_conflicts.html)
* First-class [route meta-data](https://metosin.github.io/reitit/basics/route_data.html)
* Bi-directional routing
* [Ring-router](https://metosin.github.io/reitit/ring.html) with data-driven [middleware](https://metosin.github.io/reitit/ring/compiling_middleware.html)
* [Pluggable coercion](https://metosin.github.io/reitit/ring/parameter_coercion.html) ([clojure.spec](https://clojure.org/about/spec))
* Extendable
* Fast

See [Issues](https://github.com/metosin/reitit/issues) for roadmap.

## Latest version

[![Clojars Project](http://clojars.org/metosin/reitit/latest-version.svg)](http://clojars.org/metosin/reitit)

## Quick start

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/api/ping" ::ping]
     ["/api/orders/:id" ::order]]))

(r/match-by-path router "/api/ping")
; #Match{:template "/api/ping"
;        :meta {:name ::ping}
;        :result nil
;        :params {}
;        :path "/api/ping"}

(r/match-by-name router ::order {:id 2})
; #Match{:template "/api/orders/:id",
;        :meta {:name ::order-by-id},
;        :result nil,
;        :params {:id 2},
;        :path "/api/orders/2"}
```

## Documentation

[Check out the full documentation!](https://metosin.github.io/reitit/)

## Special thanks

To all Clojure(Script) routing libs out there, expecially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [Compojure](https://github.com/weavejester/compojure) and
[Pedestal](https://github.com/pedestal/pedestal/tree/master/route).

Also to [Compojure-api](https://github.com/metosin/compojure-api), [Kekkonen](https://github.com/metosin/kekkonen) and [Ring-swagger](https://github.com/metosin/ring-swagger) and  for the data-driven syntax, coercion & stuff.

And some [Yada](https://github.com/juxt/yada) too.

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
