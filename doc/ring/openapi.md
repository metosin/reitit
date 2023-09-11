# OpenAPI Support

**Stability: alpha**

Reitit can generate [OpenAPI 3.1.0](https://spec.openapis.org/oas/v3.1.0)
documentation. The feature works similarly to [Swagger documentation](swagger.md).

The
[ring-malli-swagger](../../examples/ring-malli-swagger)
and
[ring-spec-swagger](../../examples/ring-spec-swagger)
examples also
have OpenAPI documentation.

## OpenAPI data

The following route data keys contribute to the generated swagger specification:

| key            | description |
| ---------------|-------------|
| :openapi       | map of any openapi data. Can contain keys like `:deprecated`.
| :openapi/request-content-types | vector of supported request content types. Defaults to `["application/json"]`. Only needed if you use the [:request :content :default] coercion.
| :openapi/response-content-types | vector of supported response content types. Defaults to `["application/json"]`. Only needed if you use the [:response nnn :content :default] coercion.
| :no-doc        | optional boolean to exclude endpoint from api docs
| :tags          | optional set of string or keyword tags for an endpoint api docs
| :summary       | optional short string summary of an endpoint
| :description   | optional long description of an endpoint. Supports http://spec.commonmark.org/

Coercion keys also contribute to the docs:

| key           | description |
| --------------|-------------|
| :parameters   | optional input parameters for a route, in a format defined by the coercion
| :request      | optional description of body parameters, possibly per content-type
| :responses    | optional descriptions of responses, in a format defined by coercion

## Annotating schemas

You can use malli properties, schema-tools data or spec-tools data to
annotate your models with examples, descriptions and defaults that
show up in the OpenAPI spec.

Malli:

```clj
["/plus"
 {:post
  {:parameters
   {:body [:map
           [:x
            {:title "X parameter"
             :description "Description for X parameter"
             :json-schema/default 42}
            int?]
           [:y int?]]}}}]
```

Schema:

```clj
["/plus"
 {:post
  {:parameters
   {:body {:x (schema-tools.core/schema s/Num {:description "Description for X parameter"
                                               :openapi/example 13
                                               :openapi/default 42})
           :y int?}}}}]
```

Spec:

```clj
["/plus"
 {:post
  {:parameters
   {:body (spec-tools.data-spec/spec ::foo
                                     {:x (schema-tools.core/spec {:spec int?
                                                                  :description "Description for X parameter"
                                                                  :openapi/example 13
                                                                  :openapi/default 42})
                                      :y int?}}}}}]
```

## Per-content-type coercions

Use `:request` coercion (instead of `:body`) to unlock
per-content-type coercions. This also lets you specify multiple named
examples. See [Coercion](coercion.md) for more info. See also [the
openapi example](../../examples/openapi).

```clj
["/pizza"
 {:get {:summary "Fetch a pizza | Multiple content-types, multiple examples"
        :responses {200 {:content {"application/json" {:description "Fetch a pizza as json"
                                                       :schema [:map
                                                                [:color :keyword]
                                                                [:pineapple :boolean]]
                                                       :examples {:white {:description "White pizza with pineapple"
                                                                          :value {:color :white
                                                                                  :pineapple true}}
                                                                  :red {:description "Red pizza"
                                                                        :value {:color :red
                                                                                :pineapple false}}}}
                                   "application/edn" {:description "Fetch a pizza as edn"
                                                      :schema [:map
                                                               [:color :keyword]
                                                               [:pineapple :boolean]]
                                                      :examples {:red {:description "Red pizza with pineapple"
                                                                       :value (pr-str {:color :red :pineapple true})}}}}}}
```



## Custom OpenAPI data

The `:openapi` route data key can be used to add top-level or
route-level information to the generated OpenAPI spec. This is useful
for providing `"securitySchemes"` or other OpenAPI keys that are not
generated automatically by reitit.

See [the openapi example](../../examples/openapi) for a working
example of `"securitySchemes"`.

## OpenAPI spec

Serving the OpenAPI specification is handled by `reitit.openapi/create-openapi-handler`. It takes no arguments and returns a ring handler which collects at request-time data from all routes and returns an OpenAPI specification as Clojure data, to be encoded by a response formatter.

You can use the `:openapi` route data key of the `create-openapi-handler` route to populate the top level of the OpenAPI spec.

Example:

```
["/openapi.json"
 {:get {:handler (openapi/create-openapi-handler)
        :openapi {:info {:title "my nice api" :version "0.0.1"}}
        :no-doc true}}]
```

If you need to post-process the generated spec, just wrap the handler with a custom `Middleware` or an `Interceptor`.

## Swagger-ui

[Swagger-UI](https://github.com/swagger-api/swagger-ui) is a user interface to visualize and interact with the Swagger specification. To make things easy, there is a pre-integrated version of the swagger-ui as a separate module.

Note: you need Swagger-UI 5 for OpenAPI 3.1 support. As of 2023-03-10, a v5.0.0-alpha.0 is out.
