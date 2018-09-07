# Interceptors

Reitit also support for [Pedestal](pedestal.io)-style [interceptors](http://pedestal.io/reference/interceptors) as an alternative to using middleware. Basic interceptor handling is implemented in `reitit.interceptor` package.  There is no interceptor executor shipped, but you can use libraries like [Pedestal Interceptor](https://github.com/pedestal/pedestal/tree/master/interceptor) or [Sieppari](https://github.com/metosin/sieppari) to execute the chains.

## Reitit-http

```clj
[metosin/reitit-http "0.2.1"]
```

An module for http-routing using interceptors instead of middleware. Builds on top of the [`reitit-ring`](../ring/ring.md) module. The differences:

* instead of `:middleware`, uses `:interceptors`
* compared to `reitit.http/http-router` takes an extra options map with mandatory key `:executor` (of type `reitit.interceptor/Executor`) and optional top level `:interceptors` - wrapping both routes and default handler.
* optional entry poitn `reitit.http/routing-interceptor` to provide a routing interceptor, to be used with Pedestal.

## Examples

### Sieppari

See code at: https://github.com/metosin/reitit/tree/master/examples/http

### Pedestal

See example at: https://github.com/metosin/reitit/tree/master/examples/pedestal
