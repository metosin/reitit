# Pedestal

[Pedestal](http://pedestal.io/) is a well known interceptor implmementation for Clojure. To use `reitit-http` with it, we need to change the default routing interceptor into a new one. Currently, there isn't a separate Pedestal-module in reitit, but the examples have the example code how to do this.

## Caveat

`reitit-http` defines Interceptors as `reitit.interceptor/Interceptor`. Compared to Pedestal (2-arity), reitit uses a simplified (1-arity) model for handling errors, described in the [Sieppari README](https://github.com/metosin/sieppari#differences-to-pedestal). 

* you can use any [pedestal-style interceptor](http://pedestal.io/reference/interceptors) within reitit router (as Pedestal is executing those anyway)
* you can use any reitit-style interceptor that doesn't have `:error`-stage defined
* using a reitit-style interceptor with `:error` defined will cause `ArityException` if invoked

See the [error handling guide](http://pedestal.io/reference/error-handling) on how to handle errors with Pedestal.

## Examples

### Simple

* simple example, with both sync & async code:
  * https://github.com/metosin/reitit/tree/master/examples/pedestal

### With batteries

* with [default interceptors](default_interceptors.md), [coercion](../coercion/coercion.md) and [swagger](../ring/swagger.md)-support (note: exception handling is disabled):
  * https://github.com/metosin/reitit/tree/master/examples/pedestal-swagger
