# Performance

There are many great routing libraries for Clojure(Script), but not many are optimized for perf. Reitit tries to be both great in features and really fast. Originally the routing was adopted from [Pedestal](http://pedestal.io/), but it has been since mostly rewritten.

### Rationale

* Multiple routing algorithms, select for for a given route tree
* Route flattening and re-ordering
* Managed mutability over Immutability
* Precompute/compile as much as possible (matches, middleware, routes)
* Use abstractions that enable JVM optimizations
* Use small functions to enable JVM Inlining
* Protocols over Multimethods
* Records over Maps
* Always be measuring
* Don't trust the (micro-)benchmarks

### Performance guides

As a library user, some things related to performance:

* avoid wildcard-routes - it's an order of magnitude slower to match than non-wildcard routes
* it's ok to mix non-wildcard and wildcard routes in a same routing tree as long as you don't disable the [conflict resolution](basics/route_conflicts.md) => if no conflicting routes are found, a `:mixed-router` can be created, which collects all non-wildcard routes into a separate fast subrouter.

### Example

The routing sample taken from [bide](https://github.com/funcool/bide) perf suite, run with a Late 2013 MacBook Pro, with the `perf` profile:

```clj
(require '[reitit.core :as r])
(require '[criterium.core :as cc])

(def routes
  (r/router
    [["/auth/login" :auth/login]
     ["/auth/recovery/token/:token" :auth/recovery]
     ["/workspace/:project/:page" :workspace/page]]))

;; Execution time mean : 3.488297 µs -> 286M ops/sec
(cc/quick-bench
  (dotimes [_ 1000]
    (r/match-by-path routes "/auth/login")))

;; Execution time mean : 692.905995 µs -> 1.4M ops/sec
(cc/quick-bench
  (dotimes [_ 1000]
    (r/match-by-path routes "/workspace/1/1")))
```

### Is that good?

Based on some [quick tests](https://github.com/metosin/reitit/tree/master/perf-test/clj/reitit), the first lookup is order of 100 times faster than other tested Clojure routing libraries. The second being 3-18x faster. But as like most microbenchmarks, test usually lie as they might test things with different libs. For example, Pedestal also matches on the `:request-method` which makes it do more work. With real life routing trees, the differences are most likely more subtle, or even the order might be totally different.

### So why test?

Real value of perf tests is to have a internal baseline and optimize against it. Also, to ensure that new features don't regress the performance.

It might be interesting to look out of the box and compare Clojure routing libs to routers in other languages, like the [routers in Go](https://github.com/julienschmidt/go-http-routing-benchmark).

### Plans ahead

Currently, the non-wildcard routes are already really fast to match, but wildcard routes use only a naive linear scan. Plan is to add a optimized [Trie](https://en.wikipedia.org/wiki/Trie)-based router. See
[httprouter](https://github.com/julienschmidt/httprouter#how-does-it-work) and [Pedestal](https://github.com/pedestal/pedestal/pull/330) for details. New routing algorithms can be plugged in easily, without changes in the public apis.
