# reitit [![Build Status](https://travis-ci.org/metosin/reitit.svg?branch=master)](https://travis-ci.org/metosin/reitit) [![Dependencies Status](https://jarkeeper.com/metosin/reitit/status.svg)](https://jarkeeper.com/metosin/reitit)

A friendly data-driven router for Clojure(Script).

* Simple data-driven [route syntax](https://metosin.github.io/reitit/basics/route_syntax.md)
* [Route conflict resolution](https://metosin.github.io/reitit/advanced/route_conflicts.md)
* First-class [route meta-data](https://metosin.github.io/reitit/basics/route_data.md)
* Bi-directional routing
* [Pluggable coercion](https://metosin.github.io/reitit/ring/parameter_coercion.md) ([clojure.spec](https://clojure.org/about/spec))
* supports both [Middleware](https://metosin.github.io/reitit/ring/compiling_middleware.md) & Interceptors
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

## Development instructions

The documentation is built with [gitbook](https://toolchain.gitbook.com). To preview your changes locally:

```bash
npm install -g gitbook-cli
gitbook install
gitbook serve
```

## License

Copyright © 2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
