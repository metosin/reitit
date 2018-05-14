## 0.1.1-SNAPSHOT

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

* New module to produce swagger-docs from routing tree, including `Coercion` definitions. Works with both middleware & interceptors and Schema & Spec. See [docs](https://metosin.github.io/reitit/ring/swagger.html).

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
