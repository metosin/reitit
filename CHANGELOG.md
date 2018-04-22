## 0.1.1-SNAPSHOT

### `reitit-core`

* `match-by-path` encodes parameters into strings using (internal) `reitit.impl/IntoString` protocol. Handles all of: strings, numbers, keywords, booleans, objects. Fixes [#75](https://github.com/metosin/reitit/issues/75).

```clj
(require '[reitit.core :as r])

(r/match-by-name
  (r/router
    ["/coffee/:type" ::coffee])
  ::coffee
  {:type :luwak})
;#Match{:template "/coffee/:type",
;       :data {:name :user/coffee},
;       :result nil,
;       :path-params {:type "luwak"},
;       :path "/coffee/luwak"}
```

### `reitit-swagger`

* New module to produce swagger-docs from routing tree, including `Coercion` definitions. Works with both middleware & interceptors.

```clj
(require '[reitit.ring :as ring])
(require '[reitit.swagger :as swagger])
(require '[reitit.ring.coercion :as rrc])
(require '[reitit.coercion.spec :as spec])
(require '[reitit.coercion.schema :as schema])

(require '[schema.core :refer [Int]])

(ring/ring-handler
  (ring/router
    ["/api"
     {:swagger {:id ::math}}

     ["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "my-api"}}
             :handler (swagger/create-swagger-handler)}}]

     ["/spec" {:coercion spec/coercion}
      ["/plus"
       {:get {:summary "plus"
              :parameters {:query {:x int?, :y int?}}
              :responses {200 {:body {:total int?}}}
              :handler (fn [{{{:keys [x y]} :query} :parameters}]
                         {:status 200, :body {:total (+ x y)}})}}]]

     ["/schema" {:coercion schema/coercion}
      ["/plus"
       {:get {:summary "plus"
              :parameters {:query {:x Int, :y Int}}
              :responses {200 {:body {:total Int}}}
              :handler (fn [{{{:keys [x y]} :query} :parameters}]
                         {:status 200, :body {:total (+ x y)}})}}]]]

    {:data {:middleware [rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware
                         swagger/swagger-feature]}}))
```

## 0.1.0 (2018-2-19)

* First release
