## 0.1.2-SNAPSHOT

### `reitit-core`

* Better handling of `nil` routes - they filtered away from route syntax before routes are expanded:

```clj
(testing "nil routes are allowed ans stripped"
  (is (= [] (r/routes (r/router nil))))
  (is (= [] (r/routes (r/router [nil [nil] [[nil nil nil]]]))))
  (is (= [["/ping" {} nil]] (r/routes (r/router [nil [nil] ["/ping"]]))))
  (is (= [["/ping" {} nil]] (r/routes (r/router [[[nil [nil] ["/ping"]]]])))))
```

### `reitit-schema`

* updated dependencies:

```clj
[metosin/schema-tools "0.10.3"] is available but we use "0.10.2"
```

### `reitit-swagger`

* Fix Swagger-paths, by [Kirill Chernyshov](https://github.com/DeLaGuardo).

### `reitit-swagger-ui`

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
