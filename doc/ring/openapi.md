# OpenAPI Support

**Stability: alpha**

Reitit can generate [OpenAPI 3.1.0](https://spec.openapis.org/oas/v3.1.0)
documentation. The feature works similarly to [Swagger documentation](swagger.md).

The main example is [examples/openapi](../../examples/openapi).
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
| :no-doc        | optional boolean to exclude endpoint from api docs
| :tags          | optional set of string or keyword tags for an endpoint api docs
| :summary       | optional short string summary of an endpoint
| :description   | optional long description of an endpoint. Supports http://spec.commonmark.org/
| :openapi/request-content-types | See the Per-content-type-coercions section below.
| :openapi/response-content-types |See the Per-content-type-coercions section below. vector of supported response content types. Defaults to `["application/json"]`. Only needed if you use the [:response nnn :content :default] coercion.

Coercion keys also contribute to the docs:

| key           | description |
| --------------|-------------|
| :parameters   | optional input parameters for a route, in a format defined by the coercion
| :request      | optional description of body parameters, possibly per content-type
| :responses    | optional descriptions of responses, in a format defined by coercion


## Per-content-type coercions

Use `:request` coercion (instead of `:body`) to unlock
per-content-type coercions. This also lets you specify multiple named
examples. See [Coercion](coercion.md) for more info. See also [the
openapi example](../../examples/openapi).

```clj
["/pizza"
 {:get {:summary "Fetch a pizza | Multiple content-types, multiple examples"
        :responses {200 {:description "Fetch a pizza as json or EDN"
                         :content {"application/json" {:schema [:map
                                                                [:color :keyword]
                                                                [:pineapple :boolean]]
                                                       :examples {:white {:description "White pizza with pineapple"
                                                                          :value {:color :white
                                                                                  :pineapple true}}
                                                                  :red {:description "Red pizza"
                                                                        :value {:color :red
                                                                                :pineapple false}}}}
                                   "application/edn" {:schema [:map
                                                               [:color :keyword]
                                                               [:pineapple :boolean]]
                                                      :examples {:red {:description "Red pizza with pineapple"
                                                                       :value (pr-str {:color :red :pineapple true})}}}}}}
```

The special `:default` content types map to the content types supported by the Muuntaja
instance. You can override these by using the `:openapi/request-content-types`
and `:openapi/response-content-types` keys, which must contain vector of
supported content types. If there is no Muuntaja instance, and these keys are
not defined, the content types will default to `["application/json"]`.

## OpenAPI spec

Serving the OpenAPI specification is handled by
`reitit.openapi/create-openapi-handler`. It takes no arguments and returns a
ring handler which collects at request-time data from all routes and returns an
OpenAPI specification as Clojure data, to be encoded by a response formatter.

You can use the `:openapi` route data key of the `create-openapi-handler` route
to populate the top level of the OpenAPI spec.

Example:

```
["/openapi.json"
 {:get {:handler (openapi/create-openapi-handler)
        :openapi {:info {:title "my nice api" :version "0.0.1"}}
        :no-doc true}}]
```

If you need to post-process the generated spec, just wrap the handler with a custom `Middleware` or an `Interceptor`.

## Swagger-ui

[Swagger-UI](https://github.com/swagger-api/swagger-ui) is a user interface to visualize and interact with the Swagger specification. To make things easy, there is a pre-integrated version of the swagger-ui as a separate module. See `reitit.swagger-ui/create-swagger-ui-handle`

## Finetuning the OpenAPI output

There are a number of ways you can specify extra data that gets
included in the OpenAPI spec.

### Custom OpenAPI data

The `:openapi` route data key can be used to add top-level or
route-level information to the generated OpenAPI spec.

A straightforward use case is adding `"externalDocs"`:

```clj
["/account"
 {:get {:summary "Fetch an account | Recursive schemas using malli registry, link to external docs"
        :openapi {:externalDocs {:description "The reitit repository"
                                 :url "https://github.com/metosin/reitit"}}
        ...}}]
```

In a more complex use case is providing `"securitySchemes"`. See
[the openapi example](../../examples/openapi) for a working example of
`"securitySchemes"`. See also the
[OpenAPI docs](https://spec.openapis.org/oas/v3.1.0.html#security-scheme-object)

### Annotating schemas

You can use malli properties, schema-tools data or spec-tools data to
annotate your models with examples, descriptions and defaults that
show up in the OpenAPI spec.

This approach lets you add additional keys to the
[OpenAPI Schema Objects](https://spec.openapis.org/oas/v3.1.0.html#schema-object).
The most common ones are default and example values for parameters.

Malli:

```clj
["/plus"
 {:post
  {:parameters
   {:body [:map
           [:x
            {:title "X parameter"
             :description "Description for X parameter"
             :json-schema/deprecated true
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
                                               :openapi/deprecated true
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
                                                                  :openapi/deprecated true
                                                                  :openapi/example 13
                                                                  :openapi/default 42})
                                      :y int?}}}}}]
```

### Adding examples

Adding request/response examples have been mentioned above a couple of times
above. Here's a summary of the different ways to do it:

1. Add an example to the schema object using a `:openapi/example`
   (schema, spec) or `:json-schema/example` (malli) key in your
   schema/spec/malli model metadata. See the examples above.
2. Use `:example` (a single example) or `:examples` (named examples)
   with per-content-type coercion.

**Caveat!** When adding examples for query parameters (or headers),
you must add the examples to the individual parameters, not the map
schema surrounding them. This is due to limitations in how OpenAPI
represents query parameters.

```clj
;; Wrong!
{:parameters {:query [:map
                      {:json-schema/example {:a 1}}
                      [:a :int]]}}
;; Right!
{:parameters {:query [:map
                      [:a {:json-schema/example 1} :int]]}}
```

### Named schemas

OpenAPI supports reusable schema objects that can be referred to with
the `"$ref": "#/components/schemas/Foo"` json-schema syntax. This is
useful when you have multiple endpoints that use the same schema. It
can also make OpenAPI-based code nicer for consumers of your API.
These schemas are also rendered in their own section in Swagger UI.

Reusable schema objects are generated for Malli `:ref`s and vars. The
[openapi example](../../examples/openapi) showcases this.

Currently (as of 0.7.2), reusable schema objects are **not** generated
for Plumatic Schema or Spec.
