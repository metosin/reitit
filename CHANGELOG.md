## UNRELEASED

## `reitit-core`

* **BREAKING**: the router option key to extract body format has been renamed: `:extract-request-format` => `:reitit.coercion/extract-request-format`
  * should only concern you if you are not using [Muuntaja](https://github.com/metosin/muuntaja).
* the `r/routes` returns just the path + data tuples as documented, not the compiled route results. To get the compiled results, use `r/compiled-routes` instead.
* welcome route name conflict resolution! If router has routes with same names, router can't be created. fix 'em.
* sequential child routes are allowed, enabling this:

```clj
(-> ["/api"
     (for [i (range 4)]
       [(str "/" i)])]
    (r/router)
    (r/routes))
;[["/api/0" {}]
; ["/api/1" {}]
; ["/api/2" {}]
; ["/api/3" {}]]
```

* A [Guide to compose routers](https://metosin.github.io/reitit/advanced/composing_routers.html)
* Welcome Middleware and Intercetor Registries!
  * when Keywords are used in place of middleware / interceptor, a lookup is done into Router option `::middleware/registry` (or `::interceptor/registry`) with the key. Fails fast with missing registry entries.
  * fixes [#32](https://github.com/metosin/reitit/issues/32).
  * full documentation [here](https://metosin.github.io/reitit/ring/middleware_registry.html).
  
 ```clj
(require '[reitit.ring :as ring])
(require '[reitit.middleware :as middleware])

(defn wrap-bonus [handler value]
  (fn [request]
    (handler (update request :bonus (fnil + 0) value))))

(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[:bonus 20]]}
       ["/bonus" {:middleware [:bonus10]
                 :get (fn [{:keys [bonus]}]
                        {:status 200, :body {:bonus bonus}})}]]
      {::middleware/registry {:bonus wrap-bonus
                              :bonus10 [:bonus 10]}})))

(app {:request-method :get, :uri "/api/bonus"})
; {:status 200, :body {:bonus 30}}
 ```

## `reitit-swagger`

* In case of just one swagger api per router, the swagger api doesn't have to identified, so this works now:

```clj
(require '[reitit.ring :as ring])
(require '[reitit.swagger :as swagger])
(require '[reitit.swagger-ui :as swagger-ui])

(ring/ring-handler
  (ring/router
    [["/ping"
      {:get (fn [_] {:status 200, :body "pong"})}]
     ["/swagger.json"
      {:get {:no-doc true
             :handler (swagger/create-swagger-handler)}}]])
  (swagger-ui/create-swagger-ui-handler {:path "/"}))
```

## `reitit-swagger-ui`

* **BREAKING**: pass swagger-ui `:config` as-is (instead of mixed-casing keys) to swagger-ui, fixes [#109](https://github.com/metosin/reitit/issues/109):
  * see [docs](https://github.com/swagger-api/swagger-ui/tree/2.x#parameters) for available parameters.

```clj
(swagger-ui/create-swagger-ui-handler
  {:path "/"
   :url "/api/swagger.json"
   :config {:jsonEditor true
            :validatorUrl nil}})
```

## 0.1.3 (2018-6-25)

## `reitit-core`

* `reitit.coercion/coerce!` coerced all parameters found in match, e.g. injecting in `:query-parameters` into `Match` with coerce those too if `:query` coercion is defined.
* if response coercion is not defined for a response status, response is still returned
* `spec-tools.data-spec/maybe` can be used in spec-coercion.

```clj
(def router
  (reitit.core/router
    ["/spec" {:coercion reitit.coercion.spec/coercion}
     ["/:number/:keyword" {:parameters {:path {:number int?
                                               :keyword keyword?}
                                        :query (ds/maybe {:int int?})}}]]
    {:compile reitit.coercion/compile-request-coercers}))

(-> (reitit.core/match-by-path router "/spec/10/kikka")
    (assoc :query-params {:int "10"})
    (reitit.coercion/coerce!))
; {:path {:number 10, :keyword :kikka}
;  :query {:int 10}}
```

* `reitit.core/match->path` to create full paths from match, including the query parameters:

```clj
(require '[reitit.core :as r])

(-> (r/router ["/:a/:b" ::route])
    (r/match-by-name! ::route {:a "olipa", :b "kerran"})
    (r/match->path))
; "/olipa/kerran"

(-> (r/router ["/:a/:b" ::route])
    (r/match-by-name! ::route {:a "olipa", :b "kerran"})
    (r/match->path {:iso "pöriläinen"}))
; "/olipa/kerran?iso=p%C3%B6ril%C3%A4inen"
```

### `reitit-spec`

* `[metosin/spec-tools "0.7.1"]` with swagger generation enhancements, see the [CHANGELOG](https://github.com/metosin/spec-tools/blob/master/CHANGELOG.md)
* if response coercion is not defined for a response status, no `:schema` is not emitted.
* updated dependencies:

```clj
[metosin/spec-tools "0.7.1"] is available but we use "0.7.0"
```

### `reitit-schema`

* if response coercion is not defined for a response status, no `:schema` is not emitted.

## 0.1.2 (2018-6-6)

### `reitit-core`

* Better handling of `nil` in route syntax:
  * explicit `nil` after path string is always handled as `nil` route
  * `nil` as path string causes the whole route to be `nil`
  * `nil` as child route is stripped away

```clj
(testing "nil routes are stripped"
  (is (= [] (r/routes (r/router nil))))
  (is (= [] (r/routes (r/router [nil ["/ping"]]))))
  (is (= [] (r/routes (r/router [nil [nil] [[nil nil nil]]]))))
  (is (= [] (r/routes (r/router ["/ping" [nil "/pong"]])))))
```
### `reitit-ring`

* Use HTTP redirect (302) with index-files in `reitit.ring/create-resource-handler`.
* `reitit.ring/create-default-handler` now conforms to [RING Spec](https://github.com/ring-clojure/ring/blob/master/SPEC), Fixes [#83](https://github.com/metosin/reitit/issues/83)

### `reitit-schema`

* updated dependencies:

```clj
[metosin/schema-tools "0.10.3"] is available but we use "0.10.2"
```

### `reitit-swagger`

* Fix Swagger-paths, by [Kirill Chernyshov](https://github.com/DeLaGuardo).

### `reitit-swagger-ui`

* Use HTTP redirect (302) with index-files in `reitit.swagger-ui/create-swagger-ui-handler`.

* updated dependencies:

```clj
[metosin/jsonista "0.2.1"] is available but we use "0.2.0"
```

## 0.1.1 (2018-5-20)

### `reitit-core`

* `linear-router` now works with unnamed catch-all parameters, e.g. `"/files/*"`
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

### `reitit-ring`

* `reitit.ring/default-handler` now works correctly with async ring
* new helper `reitit.ring/router` to compose routes outside of a router.
* `reitit.ring/create-resource-handler` function to serve static routes. See [docs](https://metosin.github.io/reitit/ring/static.html).

* new dependencies:

```clj
[ring/ring-core "1.6.3"]
```

### `reitit-swagger`

* New module to produce swagger-docs from routing tree, including `Coercion` definitions. Works with both middleware & interceptors and Schema & Spec. See [docs](https://metosin.github.io/reitit/ring/swagger.html) and [example project](https://github.com/metosin/reitit/tree/master/examples/ring-swagger).

### `reitit-swagger-ui`

New module to server pre-integrated [Swagger-ui](https://github.com/swagger-api/swagger-ui). See [docs](https://metosin.github.io/reitit/ring/swagger.html#swagger-ui).

* new dependencies:

```clj
[metosin/jsonista "0.2.0"]
[metosin/ring-swagger-ui "2.2.10"]
```

### dependencies

```clj
[metosin/spec-tools "0.7.0"] is available but we use "0.6.1"
[metosin/schema-tools "0.10.2"] is available but we use "0.10.1"
```

## 0.1.0 (2018-2-19)

* First release
