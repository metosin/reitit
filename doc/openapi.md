# Swagger & OpenAPI (WIP)

Goal is to support both [Swagger](https://swagger.io/) & [OpenAPI](https://www.openapis.org/) for route documentation. Documentation is extracted from existing coercion definitions `:parameters`, `:responses` and from a set of new doumentation keys.

Swagger-support draft works, but only for Clojure.

### TODO

* [metosin/schema-tools#38](https://github.com/metosin/schema-tools/issues/38): extract Schema-swagger from [ring-swagger](https://github.com/metosin/ring-swagger) into [schema-tools](https://github.com/metosin/schema-tools) to support both Clojure & ClojureScript
* separate modules for the swagger2 & openapi
* [metosin/spec-tools#105](https://github.com/metosin/spec-tools/issues/105): support Openapi

### Example

Current `reitit-swagger` draft (with `reitit-ring` & data-specs):


```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.swagger :as swagger])
(require '[reitit.ring.coercion :as rrc])
(require '[reitit.coercion.spec :as spec])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ;; identify a swagger api
       ;; there can be several in a routing tree
       {:swagger {:id :math}}

       ;; the (undocumented) swagger spec endpoint
       ["/swagger.json"
        {:get {:no-doc true
               :swagger {:info {:title "my-api"}}
               :handler swagger/swagger-spec-handler}}]

       ["/minus"
        {:get {:summary "minus"
               :parameters {:query {:x int?, :y int?}}
               :responses {200 {:body {:total int?}}}
               :handler (fn [{{{:keys [x y]} :query} :parameters}]
                          {:status 200, :body {:total (- x y)}})}}]

       ["/plus"
        {:get {:summary "plus"
               :parameters {:query {:x int?, :y int?}}
               :responses {200 {:body {:total int?}}}
               :handler (fn [{{{:keys [x y]} :query} :parameters}]
                          {:status 200, :body {:total (+ x y)}})}}]]

      {:data {:middleware [;; does not particiate in request processing
                           ;; just defines specs for the extra keys
                           swagger/swagger-middleware
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]
              :coercion spec/coercion}})))
```
