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

### Does routing performance matter?

Well, it depends. Some tested routing libs seem to spend more time resolving the routes than it takes to encode & decode a 1k JSON payload. For busy sites, this actually matters.

### Example

The routing sample taken from [bide](https://github.com/funcool/bide) README, run with a Late 2013 MacBook Pro, with the `perf` profile:

```clj
(require '[reitit.core :as r])
(require '[criterium.core :as cc])

(def routes
  (r/router
    [["/auth/login" :auth/login]
     ["/auth/recovery/token/:token" :auth/recovery]
     ["/workspace/:project/:page" :workspace/page]]))

;; Execution time mean : 3.2 µs -> 312M ops/sec
(cc/quick-bench
  (dotimes [_ 1000]
    (r/match-by-path routes "/auth/login")))

;; Execution time mean : 530 µs -> 1.9M ops/sec
(cc/quick-bench
  (dotimes [_ 1000]
    (r/match-by-path routes "/workspace/1/1")))
```

### Is that good?

Based on some [quick perf tests](https://github.com/metosin/reitit/tree/master/perf-test/clj/reitit), the first (static path) lookup is 300-500x faster and the second (wildcard path) lookup is 4-24x faster that the other tested routing libs (ataraxy, bidi, compojure and pedestal).

But, one shoudn't trust the benchmarks. Many libraries (here: compojure, pedestal and ataraxy) always match also on the request-method so they do more work. Also, real-life routing tables might look different and different libs might behave differently.

But, the perf should be good.

### Value of perf tests?

Real value of perf tests is to get a internal baseline to optimize against. Also, to ensure that new features don't regress the performance.

It might be interesting to look out of the box and compare the fast Clojure routing libs to routers in other languages, like the [routers in Go](https://github.com/julienschmidt/go-http-routing-benchmark).

### Performance guides

Few things that have an effect on performance:

* Wildcard-routes are an order of magnitude slower than static routes
* It's ok to mix non-wildcard and wildcard routes in a same routing tree as long as you don't disable the [conflict resolution](basics/route_conflicts.md) => if no conflicting routes are found, a `:mixed-router` can be created, which internally has a fast static path router and a separate wildcard-router. So, the static paths are still fast.
* Move computation from request processing time into creation time, using by compiling [middleware](ring/compiling_middleware.md) & [route data](advanced/configuring_routers.md).
