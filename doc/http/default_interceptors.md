# Default Interceptors

```clj
[metosin/reitit-interceptors "0.5.5"]
```

Just like the [ring default middleware](../ring/default_middleware.md), but for interceptors.

### Parameters handling
* `reitit.http.interceptors.parameters/parameters-interceptor` 

### Exception handling
* `reitit.http.interceptors.exception/exception-interceptor`

### Content Negotiation
* `reitit.http.interceptors.muuntaja/format-interceptor`
* `reitit.http.interceptors.muuntaja/format-negotiate-interceptor`
* `reitit.http.interceptors.muuntaja/format-request-interceptor`
* `reitit.http.interceptors.muuntaja/format-response-interceptor`

### Multipart request handling
* `reitit.http.interceptors.multipart/multipart-interceptor`

## Example app

See an example app with the default interceptors in action: https://github.com/metosin/reitit/blob/master/examples/http-swagger/src/example/server.clj.
