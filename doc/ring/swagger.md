# Swagger Support

```
[metosin/reitit-swagger "0.1.1"]
```

Reitit supports [Swagger2](https://swagger.io/) documentation, thanks to [schema-tools](https://github.com/metosin/schema-tools) and [spec-tools](https://github.com/metosin/spec-tools). Documentation is extracted from route definitions, coercion `:parameters` and `:responses` and from a set of new documentation keys.

To enable swagger-documentation for a ring-router:

1. annotate you routes with swagger-data
2. mount a swagger-handler to serve the swagger-spec
3. optionally mount a swagger-ui to visualize the swagger-spec

## Swagger data

The following route data keys contribute to the generated swagger specification:

| key           | description |
| --------------|-------------|
| :swagger      | map of any swagger-data. Must have `:id` (keyword or sequence of keywords) to identify the api
| :no-doc       | optional boolean to exclude endpoint from api docs
| :tags         | optional set of strings of keywords tags for an endpoint api docs
| :summary      | optional short string summary of an endpoint
| :description  | optional long description of an endpoint. Supports http://spec.commonmark.org/

Coercion keys also contribute to the docs:

| key           | description |
| --------------|-------------|
| :parameters   | optional input parameters for a route, in a format defined by the coercion
| :responses    | optional descriptions of responess, in a format defined by coercion

There is a `reitit.swagger.swagger-feature`, which acts as both a `Middleware` and an `Interceptor` that is not participating in any request processing - it just defines the route data specs for the routes it's mounted to. It is only needed if the [route data validation](route_data_validation.md) is turned on.

## Swagger spec

To serve the actual [Swagger Specification](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md), there is `reitit.swagger/create-swagger-handler`. It takes no arguments and returns a ring-handler which collects at request-time data from all routes for the same swagger api and returns a formatted Swagger spesification as Clojure data, to be encoded by a response formatter.

If you need to post-process the generated spec, just wrap the handler with a custom `Middleware` or an `Interceptor`.

## Swagger-ui

[Swagger-ui](https://github.com/swagger-api/swagger-ui) is a user interface to visualize and interact with the Swagger spesification. To make things easy, there is a pre-integrated version of the swagger-ui as a separate module.

```
[metosin/reitit-swagger-ui "0.1.1"]
```

`reitit.swagger-ui/create-swagger-ui-hander` can be used to create a ring-handler to serve the swagger-ui. It accepts the following options:

| key              | description |
| -----------------|-------------|
| :parameter       | optional name of the wildcard parameter, defaults to unnamed keyword `:`
| :root            | optional resource root, defaults to `"swagger-ui"`
| :url             | path to swagger endpoint, defaults to `/swagger.json`
| :path            | optional path to mount the handler to. Works only if mounted outside of a router.
| :config          | parameters passed to swaggger-ui, keys transformed into camelCase. See [the docs](https://github.com/swagger-api/swagger-ui/blob/master/docs/usage/configuration.md)

We use swagger-ui from [ring-swagger-ui](https://github.com/metosin/ring-swagger-ui), which can be easily configured from routing application. It stores files `swagger-ui` in the resource classpath.

Webjars also hosts a [version](https://github.com/webjars/swagger-ui) of the swagger-ui.

**NOTE**: Currently, swagger-ui module is just for Clojure. ClojureScript-support welcome as a PR!

## Examples

### Simple example

* two routes in a single swagger-api `::api`
* swagger-spec served from  `"/swagger.json"`
* swagger-ui mounted to `"/"`

```clj
(require '[reitit.ring :as ring])
(require '[reitit.swagger :as swagger])
(require '[reitit.swagger-ui :as swagger-ui])

(def app
  (ring/ring-handler
    (ring/router
      [["/api"
        ["/ping" {:get (constantly "ping")}]
        ["/pong" {:post (constantly "pong")}]]
       ["/swagger.json"
        {:get {:no-doc true
               :handler (swagger/create-swagger-handler)}}]]
      {:data {:swagger {:id ::api}}}) ;; for all routes
    (swagger-ui/create-swagger-ui-handler {:path "/"})))
```

The generated swagger spec:

```clj
(app {:request-method :get :uri "/swagger.json"})
;{:status 200
; :body {:swagger "2.0"
;        :x-id #{:user/api}
;        :paths {"/api/ping" {:get {}}
;                "/api/pong" {:post {}}}}}
```

Swagger-ui:

```clj
(app {:request-method :get :uri "/"})
; ... the swagger-ui index-page, configured correctly
```

### More complete example

* `clojure.spec` and `Schema` coercion
* swagger data (`:tags`, `:produces`, `:consumes`)
* swagger-spec served from  `"/api/swagger.json"`
* swagger-ui mounted to `"/"`
* [Muuntaja](https://github.com/metosin/muuntaja) for request & response formatting
* `wrap-params` to capture query & path parameters
* missed routes are handled by `create-default-handler`
* served via [ring-jetty](https://github.com/ring-clojure/ring/tree/master/ring-jetty-adapter)

Whole example project is in [`/examples/ring-swagger`](https://github.com/metosin/reitit/tree/master/examples/ring-swagger).

```clj
(require '[reitit.ring :as ring]
(require '[reitit.swagger :as swagger]
(require '[reitit.swagger-ui :as swagger-ui]
;; coercion
(require '[reitit.ring.coercion :as rrc]
(require '[reitit.coercion.spec :as spec]
(require '[reitit.coercion.schema :as schema]
(require '[schema.core :refer [Int]]
;; web server
(require '[ring.adapter.jetty :as jetty]
(require '[ring.middleware.params]
(require '[muuntaja.middleware]))

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       {:swagger {:id ::math}}

       ["/swagger.json"
        {:get {:no-doc true
               :swagger {:info {:title "my-api"}}
               :handler (swagger/create-swagger-handler)}}]

       ["/spec"
        {:coercion spec/coercion
         :swagger {:tags ["spec"]}}

        ["/plus"
         {:get {:summary "plus with spec"
                :parameters {:query {:x int?, :y int?}}
                :responses {200 {:body {:total int?}}}
                :handler (fn [{{{:keys [x y]} :query} :parameters}]
                           {:status 200
                            :body {:total (+ x y)}})}}]]

       ["/schema"
        {:coercion schema/coercion
         :swagger {:tags ["schema"]}}

        ["/plus"
         {:get {:summary "plus with schema"
                :parameters {:query {:x Int, :y Int}}
                :responses {200 {:body {:total Int}}}
                :handler (fn [{{{:keys [x y]} :query} :parameters}]
                           {:status 200
                            :body {:total (+ x y)}})}}]]]

      {:data {:middleware [ring.middleware.params/wrap-params
                           muuntaja.middleware/wrap-format
                           swagger/swagger-feature
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]
              :swagger {:produces #{"application/json"
                                    "application/edn"
                                    "application/transit+json"}
                        :consumes #{"application/json"
                                    "application/edn"
                                    "application/transit+json"}}}})
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/", :url "/api/swagger.json"})
      (ring/create-default-handler))))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))
```

http://localhost:3000 should render now the swagger-ui:

![Swagger-ui](../images/swagger.png)

## Advanced

Route data in path `[:swagger :id]` can be either a keyword or a sequence of keywords. This enables one route to be part of multiple swagger apis. Normal route data [scoping rules](../basics/route_data.md#nested-route-data) rules apply.

Example with:

* 4 routes
* 2 swagger apis `::one` and `::two`
* 3 swagger specs

```clj
(require '[reitit.ring :as ring])
(require '[reitit.swagger :as swagger])

(def ping-route
  ["/ping" {:get (constantly "ping")}])

(def spec-route
  ["/swagger.json"
   {:get {:no-doc true
          :handler (swagger/create-swagger-handler)}}])

(def app
  (ring/ring-handler
    (ring/router
      [["/common" {:swagger {:id #{::one ::two}}} ping-route]
       ["/one" {:swagger {:id ::one}} ping-route spec-route]
       ["/two" {:swagger {:id ::two}} ping-route spec-route
        ["/deep" {:swagger {:id ::one}} ping-route]]
       ["/one-two" {:swagger {:id #{::one ::two}}} spec-route]])))
```

```clj
(-> {:request-method :get, :uri "/one/swagger.json"} app :body :paths keys)
; ("/common/ping" "/one/ping" "/two/deep/ping")
```

```clj
(-> {:request-method :get, :uri "/two/swagger.json"} app :body :paths keys)
; ("/common/ping" "/two/ping")
```

```clj
(-> {:request-method :get, :uri "/one-two/swagger.json"} app :body :paths keys)
; ("/common/ping" "/one/ping" "/two/ping" "/two/deep/ping")
```

### TODO

* create a data-driven version of [Muuntaja](https://github.com/metosin/muuntaja) that integrates into `:produces` and `:consumes`
* ClojureScript
  * example for [Macchiato](https://github.com/macchiato-framework)
  * body formatting
  * resource handling
