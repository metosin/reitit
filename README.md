# reitit [![Build Status](https://travis-ci.org/metosin/reitit.svg?branch=master)](https://travis-ci.org/metosin/reitit) [![Dependencies Status](https://jarkeeper.com/metosin/reitit/status.svg)](https://jarkeeper.com/metosin/reitit)

A friendly data-driven router for Clojure(Script).

* Simple data-driven [route syntax](#route-syntax)
* First-class [route meta-data](#route-meta-data)
* Generic, not tied to HTTP
* [Route conflict resolution](#route-conflicts)
* [Pluggable coercion](#parameter-coercion) ([clojure.spec](https://clojure.org/about/spec))
* both [Middleware](#middleware) & Interceptors
* Extendable
* Fast

Ships with example router for [Ring](#ring). See [Issues](https://github.com/metosin/reitit/issues) for roadmap.

## Latest version

[![Clojars Project](http://clojars.org/metosin/reitit/latest-version.svg)](http://clojars.org/metosin/reitit)

## Documentation

[Check out the full documentation!](https://metosin.github.io/reitit/)

## Special thanks

To all Clojure(Script) routing libs out there, expecially to
[Ataraxy](https://github.com/weavejester/ataraxy), [Bide](https://github.com/funcool/bide), [Bidi](https://github.com/juxt/bidi), [Compojure](https://github.com/weavejester/compojure) and
[Pedestal](https://github.com/pedestal/pedestal/tree/master/route).

Also to [Compojure-api](https://github.com/metosin/compojure-api), [Kekkonen](https://github.com/metosin/kekkonen) and [Ring-swagger](https://github.com/metosin/ring-swagger) and  for the data-driven syntax, coercion & stuff.

And some [Yada](https://github.com/juxt/yada) too.

## License

Copyright © 2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
