# Swagger

Reitit supports [Swagger](https://swagger.io/) to generate route documentation. Documentation is extracted from existing coercion definitions `:parameters`, `:responses` and from a set of new doumentation keys.

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
               :handler (swagger/create-swagger-handler)}}]

       ;; the (undocumented) swagger-ui
       ;; [org.webjars/swagger-ui "3.13.4"]
       ["/docs/*"
        {:get {:no-doc true
               :handler (ring/create-resource-handler
                          {:root "META-INF/resources/webjars/swagger-ui"})}}]

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
