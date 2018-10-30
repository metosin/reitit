# Interceptors

Reitit also support for [interceptors](http://pedestal.io/reference/interceptors) as an alternative to using middleware. Basic interceptor handling is implemented in `reitit.interceptor` package.  There is no interceptor executor shipped, but you can use libraries like [Pedestal Interceptor](https://github.com/pedestal/pedestal/tree/master/interceptor) or [Sieppari](https://github.com/metosin/sieppari) to execute the chains.

## Reitit-http

```clj
[metosin/reitit-http "0.2.5"]
```

An module for http-routing using interceptors instead of middleware. Builds on top of the [`reitit-ring`](../ring/ring.md) module having all the same features.

The differences:

* instead of `:middleware`, uses `:interceptors`.
* `reitit.http/http-router` requires an extra option `:executor` of type `reitit.interceptor/Executor`.
* instead of creating a ring-handler, apps can be wrapped into a routing interceptor that enqueues the matched interceptors into the context. For this, there is `reitit.http/routing-interceptor`.

## Why interceptors?

* https://quanttype.net/posts/2018-08-03-why-interceptors.html
* https://www.reddit.com/r/Clojure/comments/9csmty/why_interceptors/
