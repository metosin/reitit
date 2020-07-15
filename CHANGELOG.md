# Reitit CHANGELOG

We use [Break Versioning][breakver]. The version numbers follow a `<major>.<minor>.<patch>` scheme with the following intent:

| Bump    | Intent                                                     |
| ------- | ---------------------------------------------------------- |
| `major` | Major breaking changes -- check the changelog for details. |
| `minor` | Minor breaking changes -- check the changelog for details. |
| `patch` | No breaking changes, ever!!                                |

`-SNAPSHOT` versions are preview versions for upcoming releases.

[breakver]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md

## 0.5.5 (2020-07-15)

* recompile with Java8

```clj
[metosin/malli "0.0.1-20200715.082439-21"] is available but we use "0.0.1-20200713.080243-20"
```

## 0.5.4 (2020-07-13)

```clj
[metosin/malli "0.0.1-20200713.080243-20"] is available but we use "0.0.1-20200709.163702-18"
```

## 0.5.3 (2020-07-09)

```clj
[metosin/malli "0.0.1-20200709.163702-18"] is available but we use "0.0.1-20200525.162645-15"
```

## 0.5.2 (2020-05-27)

```clj
[metosin/malli "0.0.1-20200525.162645-15"] is available but we use "0.0.1-20200404.091302-14"
```

###  `reitit-malli`

* Fixed coercion with `:and` and `:or`, fixes [#407](https://github.com/metosin/reitit/issues/407).
* New options to `reitit.coercion.malli/create`:
  * `:validate` - boolean to indicate whether validation is enabled (true)
  * `:enabled` - boolean to indicate whether coercion (and validation) is enabled (true)

### `reitit-swagger`

* If no `:responses` are defined for an endpoint, add `{:responses {:default {:description ""}}}` to make swagger spec valid, fixes [#403](https://github.com/metosin/reitit/issues/403) by [胡雨軒 Петр](https://github.com/piotr-yuxuan).

### `reitit-ring`

* Coercion middleware will not to mount if the selected `:coercion` is not enabled for the given `:parameters`, e.g. "just api-docs"

### `reitit-http`

* Coercion interceptor will not to mount if the selected `:coercion` is not enabled for the given `:parameters`, e.g. "just api-docs"

## 0.5.1 (2020-05-18)

```clj
[metosin/sieppari "0.0.0-alpha13"] is available but we use "0.0.0-alpha10"
```

* new sieppari NOT to include all it's dev dependencies, fixes [#402](https://github.com/metosin/reitit/issues/402)
* re-compile with Java8

## 0.5.0 (2020-05-17)

* **NOTE** Due to [issues with Jackson versioning](https://clojureverse.org/t/depending-on-the-right-versions-of-jackson-libraries/5111), you might get errors after updating as [Cheshire still uses older version](https://github.com/dakrone/cheshire/pull/164) as is most likely as a transitive dependency via 3rd party libs. To resolve issues (with Leiningen), you can either:
  1. move `[metosin/reitit "0.5.0"]` as the first dependency (Lein will pick up the latest versions from there)
  2. add `[metosin/jsonista "0.2.5"]` as the first dependency
  3. add explicit dependencies to `[com.fasterxml.jackson.core/jackson-core "2.11.0"]` and `[com.fasterxml.jackson.core/jackson-databind "2.11.0"]` directly

* Updated deps:

```clj
[metosin/sieppari "0.0.0-alpha10"] is available but we use "0.0.0-alpha8"
[metosin/malli "0.0.1-20200404.091302-14"] is available but we use "0.0.1-20200305.102752-13"
[metosin/ring-swagger-ui "3.25.3"] is available but we use "2.2.10"
[metosin/spec-tools "0.10.3"] is available but we use "0.10.0"
[metosin/schema-tools "0.12.2"] is available but we use "0.12.1"
[metosin/muuntaja "0.6.7"] is available but we use "0.6.6"
[metosin/jsonista "0.2.6"] is available but we use "0.2.5"
[com.bhauman/spell-spec "0.1.2"] is available but we use "0.1.1"
[fipp "0.6.23"] is available but we use "0.6.22"
[ring/ring-core "1.8.1"] is available but we use "1.8.0"
```

### `reitit-core`

* Route conflict resolution and thus, router creation is now an order of magnitude faster.
* Forcing router to be `reitit.core/linear-router` and disabling route conflict resolution totally bypasses route conflict resolution. For cases when router creating speed matters over routing performance:

```clj
(r/router ...zillions-of-routes... {:router r/linear-router, :conflicts nil})
```

### `reitit-frontend`

* `reitit.frontend.easy` state is setup before user `on-navigate` callback
is called the first time, so that `rfe/push-state` and such can be called
([#315](https://github.com/metosin/reitit/issues/315))

### `reitit-ring`

* `reitit.ring/routes` strips away `nil` routes, fixes [#394](https://github.com/metosin/reitit/issues/394)
* `reitit.ring/create-file-handler` to serve files from filesystem, fixes [#395](https://github.com/metosin/reitit/issues/395)
* **BREAKING**: router option `:reitit.ring/default-options-handler` is deprecated 
  * fails with router creation time error
  * use `:reitit.ring/default-options-endpoint` instead, takes an expandable route data instead just of a handler.

### `reitit-http`

* **BREAKING**: router option `:reitit.http/default-options-handler` is deprecated 
  * fails with router creation time error
  * use `:reitit.http/default-options-endpoint` instead, takes an expandable route data instead just of a handler.

### `reitit-spec`

* lots of bug fixes, see [spec-tools changelog](https://github.com/metosin/spec-tools/blob/master/CHANGELOG.md#0102-2020-05-05)

###  `reitit-malli`

* Swagger body-parameters don't use empty default, fixes [#399](https://github.com/metosin/reitit/issues/399)

### `reitit-sieppari`

* changes from Sieppari:
  * fixed performance regression bugs, order of magnitude faster dispatching
  * **BREAKING**: Out-of-the-box support for `core.async` and `manifold` are dropped, to use them, one needs to explicitely require the following side-effecting namespaces:
    * `sieppari.async.core-async` for core.async
    * `sieppari.async.manifold` for manifold

### `reitit-swagger`

* default to the new swagger-ui (3.25.3), to get old back add a dependency to:

```clj
[metosin/ring-swagger-ui "2.2.10"]
```

## 0.4.2 (2020-01-17)

### `reitit`

* Updated deps:

```
[com.fasterxml.jackson.core/jackson-core "2.10.0"]
```

* See https://clojureverse.org/t/depending-on-the-right-versions-of-jackson-libraries/5111

## 0.4.1 (2020-01-14)

### `reitit`

* Updated deps:

```
[metosin/reitit-malli "0.4.1"]
```

## 0.4.0 (2020-01-14)

* Updated deps:

```
[metosin/muuntaja "0.6.6"] is available but we use "0.6.5"
[fipp "0.6.22"] is available but we use "0.6.21"
[expound "0.8.4"] is available but we use "0.7.2"
[ring/ring-core "1.8.0"] is available but we use "1.7.1"
[metosin/schema-tools "0.12.1"] is available but we use "0.12.0"
```

### `reitit-core`

* Added ability to mark individual routes as conflicting by using `:conflicting` route data. See [documentation](https://metosin.github.io/reitit/basics/route_conflicts.html). Fixes [#324](https://github.com/metosin/reitit/issues/324)
* Encode sequential and set values as multi-valued query params (e.g. `{:foo ["bar", "baz"]}` ↦ `foo=bar&foo=baz`).

### `reitit-malli`

* Alpha of [malli](https://github.com/metosin/malli)-based coercion! See [example project](./examples/ring-malli-swagger).

### `reitit-spec`

* **BREAKING**: `:body` coercion defaults to `spec-tools.core/strip-extra-keys-transformer`, so effectively all non-specced `s/keys` keys are stripped also for non-JSON formats.

### `reitit-frontend`

* **BREAKING**: Decode multi-valued query params correctly into seqs (e.g. `foo=bar&foo=baz` ↦ `{:foo ["bar", "baz"]}`).
  * Previously you'd get only the first value. (e.g. `foo=bar&foo=baz` ↦ `{:foo "bar"}`)
  
### `reitit-ring`

* **BREAKING**: New validation rule: `:middleware` must be a vector, not a list. Fixes [#296](https://github.com/metosin/reitit/issues/296). ([#319](https://github.com/metosin/reitit/pull/319) by [Daw-Ran Liou](https://github.com/dawran6))

### `reitit-middleware`

* Added support for [metosin/ring-http-response](https://github.com/metosin/ring-http-response) to exception middleware. ([#342](https://github.com/metosin/reitit/pull/342) by [Matt Russell](https://github.com/mgrbyte))

## 0.3.10 (2019-10-08)

* Updated deps:

```clj
[metosin/spec-tools "0.10.0"] is available but we use "0.9.3"
[metosin/muuntaja "0.6.5"] is available but we use "0.6.4"
[metosin/jsonista "0.2.5"] is available but we use "0.2.3"
[fipp "0.6.21"] is available but we use "0.6.18"
```

### `reitit-frontend`

* **Html5History**: Added `:ignore-anchor-click?` option ([#259](https://github.com/metosin/reitit/pull/259))
    * This option can used to provide custom function which determines if clicks in anchor elements are ignored.
    * Default logic can be extended by using `reitit.frontend.history/ignore-anchor-click?` in custom function.
* **Html5History**: Keep URL fragments when handling anchor element clicks ([#300](https://github.com/metosin/reitit/pull/300))

## 0.3.9 (2019-06-16)

### `reitit-ring`

* Added async support for `default-options-handler` on `reitit-ring`, fixes [#293](https://github.com/metosin/reitit/issues/293)

## 0.3.8 (2019-06-15)

* Updated dependencies:

```clj
[metosin/schema-tools "0.12.0"] is available but we use "0.11.0"
[metosin/spec-tools "0.9.3"] is available but we use "0.9.2"
[metosin/jsonista "0.2.3"] is available but we use "0.2.2"
```

### `reitit-core`

* Schema coercion supports transformtatins from keywords->clojure, via [schema-tools](https://github.com/metosin/schema-tools).

* Add support for explixit selection of router path-parameter `:syntax`, fixes [#276](https://github.com/metosin/reitit/issues/276)

```clj
(require '[reitit.core :as r])

;; default
(-> (r/router
      ["http://localhost:8080/api/user/{id}" ::user-by-id])
    (r/match-by-path "http://localhost:8080/api/user/123"))
;#Match{:template "http://localhost:8080/api/user/{id}",
;       :data {:name :user/user-by-id},
;       :result nil,
;       :path-params {:id "123", :8080 ":8080"},
;       :path "http://localhost:8080/api/user/123"}


;; just bracket-syntax
(-> (r/router
      ["http://localhost:8080/api/user/{id}" ::user-by-id]
      {:syntax :bracket})
    (r/match-by-path "http://localhost:8080/api/user/123"))
;#Match{:template "http://localhost:8080/api/user/{id}",
;       :data {:name :user/user-by-id},
;       :result nil,
;       :path-params {:id "123"},
;       :path "http://localhost:8080/api/user/123"}
```

## 0.3.7 (2019-05-25)

### `reitit-pedestal`

* Fixed Pedestal Interceptor coercion bug, see [#285](https://github.com/metosin/reitit/issues/285).

## 0.3.6 (2019-05-23)

* Fixed [a zillion typos](https://github.com/metosin/reitit/pull/281) in docs by [Marcus Spiegel](https://github.com/malesch).

### `reitit-ring`

* Fix on `reitit.ring/create-default-handler` to support overriding just some handlers, fixes [#283](https://github.com/metosin/reitit/issues/283), by [Daniel Sunnerek](https://github.com/kardan).

## 0.3.5 (2019-05-22)

### `reitit-core`

* **MAJOR**: Fix bug in Java Trie (since 0.3.0!), [which made invalid path parameter parsing in concurrent requests](https://github.com/metosin/reitit/issues/277). All Trie implementation classes are final from now on.

## 0.3.4 (2019-05-20)

### `reitit-core`

* Spec problems are [reported correctly in coercion](https://github.com/metosin/reitit/pull/275) by [Kevin W. van Rooijen](https://github.com/kwrooijen).

## 0.3.3 (2019-05-16)

* Better error messages on route data merge error:

```clj
(ns user
  (:require [reitit.core :as r]
            [schema.core :as s]
            [reitit.dev.pretty :as pretty]))

(r/router
  ["/kikka"
   {:parameters {:body {:id s/Str}}}
   ["/kakka"
    {:parameters {:body [s/Str]}}]]
  {:exception pretty/exception})
; -- Router creation failed -------------------------------------------- user:7 --
; 
; Error merging route-data:
; 
; -- On route -----------------------
; 
; /kikka/kakka
; 
; -- Exception ----------------------
; 
; Don't know how to create ISeq from: java.lang.Class
; 
;    {:parameters {:body {:id java.lang.String}}}
; 
;    {:parameters {:body [java.lang.String]}}
; 
; https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-data
; 
; --------------------------------------------------------------------------------
```

## 0.3.2 (2019-05-13)

* Updated dependencies:

```clj
[metosin/spec-tools "0.9.2"] is available but we use "0.9.0"
[metosin/muuntaja "0.6.4"] is available but we use "0.6.3"
[fipp "0.6.18"] is available but we use "0.6.17"
[lambdaisland/deep-diff "0.0-47"] is available but we use "0.0-25"
```

* Updated guides on [Error Messages](https://metosin.github.io/reitit/basics/error_messages.html) & [Route-data Validation](https://metosin.github.io/reitit/basics/route_data_validation.html)

### `reitit-core`

* new options `:reitit.spec/wrap` to wrap top-level route data specs when spec validation is enabled. Using `spec-tools.spell/closed` closes top-level specs. 
  * Updated swagger-examples to easily enable closed spec validation

```clj
(require '[reitit.core :as r])
(require '[reitit.spec :as rs])
(require '[reitit.dev.pretty :as pretty)
(require '[spec-tools.spell :as spell])
(require '[clojure.spec.alpha :as s])

(s/def ::description string?)

(r/router
  ["/api" {:summary "kikka"}]
  {:validate rs/validate
   :spec (s/merge ::rs/default-data (s/keys :req-un [::description]))
   ::rs/wrap spell/closed
   :exception pretty/exception})
```

![closed](./doc/images/closed-spec1.png)

### `reitit-frontend`

* add support for html5 links inside Shadow DOM by [Antti Leppänen](https://github.com/fraxu).
* lot's of React-router [examples](./examples) ported in, thanks to [Valtteri Harmainen](https://github.com/vharmain)

### `reitit.pedestal`

* Automatically coerce Sieppari-style 1-arity `:error` handlers into Pedestal-style 2-arity `:error` handlers. Thanks to [Mathieu MARCHANDISE](https://github.com/vielmath).

### `reitit-middleware`

* `reitit.ring.middleware.dev/print-request-diffs` prints also response diffs.

<img src="https://user-images.githubusercontent.com/567532/56895987-3e54ea80-6a93-11e9-80ee-9ba6f8896db6.png">

## 0.3.1 (2019-03-18)

* Recompiled with Java8 as target, fixes [#241](https://github.com/metosin/reitit/issues/241).

## 0.3.0 (2019-03-17)

### `reitit-core`

* welcome new wildcard routing!
  * optional bracket-syntax with parameters
     * `"/user/:user-id"` = `"/user/{user-id}"`
     * `"/assets/*asset"` = `"/assets/{*asset}`
  * enabling qualified parameters
     * `"/user/{my.user/id}/{my.order/id}"`
  * parameters don't have to span whole segments
     * `"/file-:id/topics"` (free start, ends at slash)
     * `"/file-{name}.html"` (free start & end)
  * backed by a new `:trie-router`, replacing `:segment-router`
     * [up to 2x faster](https://metosin.github.io/reitit/performance.html) on the JVM

* **BREAKING**: `reitit.spec/validate-spec!` has been renamed to `validate`
* With `clojure.spec` coercion, values flow through both `st/coerce` & `st/conform` yielding better error messages. Original issue in [compojure-api](https://github.com/metosin/compojure-api/issues/409).

### `reitit-dev`

* new module for friendly router creation time exception handling
  * new option `:exception` in `r/router`, of type `Exception => Exception` (default `reitit.exception/exception`)
  * new exception pretty-printer `reitit.dev.pretty/exception`, based on [fipp](https://github.com/brandonbloom/fipp) and [expound](https://github.com/bhb/expound) for human readable, newbie-friendly errors.
  
#### Conflicting paths
 
```clj
(require '[reitit.core :as r])
(require '[reitit.dev.pretty :as pretty])

(r/router
  [["/ping"]
   ["/:user-id/orders"]
   ["/bulk/:bulk-id"]
   ["/public/*path"]
   ["/:version/status"]]
  {:exception pretty/exception})
```

<img src="https://gist.githubusercontent.com/ikitommi/ff9b091ffe87880d9847c9832bbdd3d2/raw/0e185e07e4ac49109bb653b4ad4656896cb41b2f/path-conflicts.png" width=640>

#### Route data error

```clj
(require '[reitit.spec :as spec])
(require '[clojure.spec.alpha :as s])

(s/def ::role #{:admin :user})
(s/def ::roles (s/coll-of ::role :into #{}))

(r/router
  ["/api/admin" {::roles #{:adminz}}]
  {:validate spec/validate
   :exception pretty/exception})
```

<img src="https://gist.githubusercontent.com/ikitommi/ff9b091ffe87880d9847c9832bbdd3d2/raw/0e185e07e4ac49109bb653b4ad4656896cb41b2f/route-data-error.png" width=640>

### `reitit-frontend`

* Frontend controllers redesigned
    * Controller `:params` function has been deprecated
    * Controller `:identity` function works the same as `:params`
    * New `:parameters` option can be used to declare which parameters
    controller is interested in, as data, which should cover most
    use cases: `{:start start-fn, :parameters {:path [:foo-id]}}`
* Ensure HTML5 History routing works with IE11

### `reitit-ring`

* Allow Middleware to compile to `nil` with Middleware Registries, fixes to [#216](https://github.com/metosin/reitit/issues/216).
* **BREAKING**: `reitit.ring.spec/validate-spec!` has been renamed to `validate`

### `reitit-http`

* Allow Interceptors to compile to `nil` with Interceptor Registries, related to [#216](https://github.com/metosin/reitit/issues/216).
* **BREAKING**: `reitit.http.spec/validate-spec!` has been renamed to `validate`

## Dependencies

* updated:

```clj
[metosin/spec-tools "0.9.0"] is available but we use "0.8.3"
[metosin/schema-tools "0.11.0"] is available but we use "0.10.5"
```

## 0.2.13 (2019-01-26)

* Don't throw `StringIndexOutOfBoundsException` with empty path lookup on wildcard paths, fixes [#209](https://github.com/metosin/reitit/issues/209)

## 0.2.12 (2019-01-18)

* fixed reflection & boxed math warnings, fixes [#207](https://github.com/metosin/reitit/issues/207)
* fixed arity-error on default routes with `reitit-ring` & `reitit-http` when `:inject-router?` set to `false`.

## 0.2.11 (2019-01-17)

* new guide on [pretty printing spec coercion errors with expound](https://metosin.github.io/reitit/ring/coercion.html#pretty-printing-spec-errors), fixes [#153](https://github.com/metosin/reitit/issues/153).

### `reitit-core`

* `reitit.core/Expand` can be extended, fixes [#201](https://github.com/metosin/reitit/issues/201).
* new snappy Java-backed `SegmentTrie` routing algorithm and data structure backing `reitit.core/segment-router`, making wildcard routing 2x faster on the JVM
* `reitit.core/linear-router` uses the segment router behind the scenes, 2-4x faster on the JVM too.

### `reitit-ring`

* new options `:inject-match?` and `:inject-router?` on `reitit.ring/ring-handler` to optionally not to inject `Router` and `Match` into the request. See [performance guide](https://metosin.github.io/reitit/performance.html#faster!) for details.

### `reitit-http`

* new options `:inject-match?` and `:inject-router?` on `reitit.http/ring-handler` and `reitit.http/routing-interceptor` to optionally not to inject `Router` and `Match` into the request. See [performance guide](https://metosin.github.io/reitit/performance.html#faster!) for details.

### dependencies

* updated:

```clj
[metosin/spec-tools "0.8.3"] is available but we use "0.8.2"
```

## 0.2.10 (2018-12-30)

### `reitit-core`

* `segment-router` doesn't accept empty segments as path-parameters, fixes [#181](https://github.com/metosin/reitit/issues/181).
* path-params are decoded correctly with `r/match-by-name`, fixes [#192](https://github.com/metosin/reitit/issues/192).
* new `:quarantine-router`, which is uses by default if there are any path conflicts: uses internally `:mixed-router` for non-conflicting routes and `:linear-router` for conflicting routes.

```clj
(-> [["/joulu/kinkku"]   ;; linear-router
     ["/joulu/:torttu"]  ;; linear-router
     ["/tonttu/:id"]     ;; segment-router
     ["/manna/puuro"]    ;; lookup-router
     ["/sinappi/silli"]] ;; lookup-router
    (r/router {:conflicts nil})
    (r/router-name))
; => :quarantine-router
```

* `reitit.interceptor/transform-butlast` helper to transform the interceptor chains (last one is usually the handler).

## `reitit-middleware`

* `reitit.ring.middleware.dev/print-request-diffs` middleware transformation function to print out request diffs between middleware to the console
  * read the [docs](https://metosin.github.io/reitit/ring/transforming_middleware_chain.html#printing-request-diffs)
  * see [example app](https://github.com/metosin/reitit/tree/master/examples/ring-swagger)

<img src="https://metosin.github.io/reitit/images/ring-request-diff.png" width=320>

## `reitit-interceptors`

* `reitit.http.interceptors.dev/print-context-diffs` interceptor transformation function to print out context diffs between interceptor steps to the console:
  * read the [docs](https://metosin.github.io/reitit/http/transforming_interceptor_chain.html#printing-context-diffs)
  * see [example app](https://github.com/metosin/reitit/tree/master/examples/http-swagger)

<img src="https://metosin.github.io/reitit/images/http-context-diff.png" width=320>

## `reitit-sieppari`

* New version of Sieppari allows interceptors to run on ClojureScript too.

### `reitit-pedestal`

* new optional module for [Pedestal](http://pedestal.io/) integration. See [the docs](https://metosin.github.io/reitit/http/pedestal.html).

### dependencies

* updated:

```clj
[metosin/muuntaja "0.6.3"] is available but we use "0.6.1"
[metosin/sieppari "0.0.0-alpha6"] is available but we use "0.0.0-alpha7"
```

## 0.2.9 (2018-11-21)

### `reitit-spec`

* support for vector data-specs for request & response parameters by [Heikki Hämäläinen](https://github.com/hjhamala).

## 0.2.8 (2018-11-18)

### `reitit-core`

* Added support for composing middleware & interceptor transformations, fixes [#167](https://github.com/metosin/reitit/issues/167).

### `reitit-spec`

* Spec problems are exposed as-is into request & response coercion errors, enabling pretty-printers like [expound](https://github.com/bhb/expound) to be used:

```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.middleware.exception :as exception])
(require '[reitit.ring.coercion :as coercion])
(require '[expound.alpha :as expound])

(defn coercion-error-handler [status]
  (let [printer (expound/custom-printer {:theme :figwheel-theme, :print-specs? false})
        handler (exception/create-coercion-handler status)]
    (fn [exception request]
      (printer (-> exception ex-data :problems))
      (handler exception request))))

(def app
  (ring/ring-handler
    (ring/router
      ["/plus"
       {:get
        {:parameters {:query {:x int?, :y int?}}
         :responses {200 {:body {:total pos-int?}}}
         :handler (fn [{{{:keys [x y]} :query} :parameters}]
                    {:status 200, :body {:total (+ x y)}})}}]
      {:data {:coercion reitit.coercion.spec/coercion
              :middleware [(exception/create-exception-middleware
                             (merge
                               exception/default-handlers
                               {:reitit.coercion/request-coercion (coercion-error-handler 400)
                                :reitit.coercion/response-coercion (coercion-error-handler 500)}))
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware]}})))

(app
  {:uri "/plus"
   :request-method :get
   :query-params {"x" "1", "y" "fail"}})

(app
  {:uri "/plus"
   :request-method :get
   :query-params {"x" "1", "y" "-2"}})
```

### `reitit-swagger`

* `create-swagger-handler` support now 3-arity ring-async, thanks to [Miloslav Nenadál](https://github.com/nenadalm)

## 0.2.7 (2018-11-11)

### `reitit-spec`

* Fixes [spec coercion issue with aliased specs](https://github.com/metosin/spec-tools/issues/145)

* updated deps:

```clj
[metosin/spec-tools "0.8.2"] is available but we use "0.8.1"
```

## 0.2.6 (2018-11-09)

### `reitit-core`

* Faster path-parameter decoding: doing less work when parameters don't need decoding. Wildcard-routing is now 10-15% faster in perf tests (opensensors & github api).
* Fixed a ClojureScript compiler warning about private var usage. [#169](https://github.com/metosin/reitit/issues/169)

### `reitit-ring`

* `redirect-trailing-slash-handler` can strip multiple slashes from end of the uri, by [Hannu Hartikainen](https://github.com/dancek).
* Fixed a ClojureScript compiler warning about `satisfies?` being a macro.

* updated deps:

```clj
[ring "1.7.1"] is available but we use "1.7.0"
```

### `reitit-spec`

* updated deps:

```clj
[metosin/spec-tools "0.8.1"] is available but we use "0.8.0"
```

### `reitit-schema`

* updated deps:

```clj
[metosin/schema-tools "0.10.5"] is available but we use "0.10.4"
```

### `reitit-sieppari`

* updated deps:

```clj
[metosin/sieppari "0.0.0-alpha6"] is available but we use "0.0.0-alpha5"
```

## 0.2.5 (2018-10-30)

### `reitit-ring`

* router is injected into request also in the default branch
* new `reitit.ring/redirect-trailing-slash-handler` to [handle trailing slashes](https://metosin.github.io/reitit/ring/slash_handler.html) with style!
  * Fixes [#92](https://github.com/metosin/reitit/issues/92), thanks to [valerauko](https://github.com/valerauko).

```clj
(require '[reitit.ring :as ring])

(def app
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200, :body ""})]
       ["/pong/" (constantly {:status 200, :body ""})]])
    (ring/redirect-trailing-slash-handler)))

(app {:uri "/ping/"})
; {:status 308, :headers {"Location" "/ping"}, :body ""}

(app {:uri "/pong"})
; {:status 308, :headers {"Location" "/pong/"}, :body ""}
```

* updated deps:

```clj
[ring/ring-core "1.7.1"] is available but we use "1.7.0"
```

### `reitit-http`

* router is injected into request also in the default branch

## 0.2.4 (2018-10-21)

### `reitit-ring`

* New option `:not-found-handler` in `reitit.ring/create-resource-handler` to set how 404 is handled. Fixes [#89](https://github.com/metosin/reitit/issues/89), thanks to [valerauko](https://github.com/valerauko).

### `reitit-spec`

* Latest features from [spec-tools](https://github.com/metosin/spec-tools)
  * Swagger enhancements
  * Better spec coercion via `st/coerce` using spec walking & inference: many simple specs (core predicates, `spec-tools.core/spec`, `s/and`, `s/or`, `s/coll-of`, `s/keys`, `s/map-of`, `s/nillable` and `s/every`) can be transformed without needing spec to be wrapped. Fallbacks to old conformed based approach.
  * [example app](https://github.com/metosin/reitit/blob/master/examples/ring-spec-swagger/src/example/server.clj).

* updated deps:

```clj
[metosin/spec-tools "0.8.0"] is available but we use "0.7.1"
```

## 0.2.3 (2018-09-24)

### `reitit-ring`

* `ring-handler` takes optionally a 3rd argument, an options map which can be used to se top-level middleware, applied before any routing is done:

```clj
(require '[reitit.ring :as ring])

(defn wrap [handler id]
  (fn [request]
    (handler (update request ::acc (fnil conj []) id))))

(defn handler [{::keys [acc]}]
  {:status 200, :body (conj acc :handler)})

(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [[mw :api]]}
       ["/get" {:get handler}]])
    (ring/create-default-handler)
    {:middleware [[mw :top]]}))

(app {:request-method :get, :uri "/api/get"})
; {:status 200, :body [:top :api :ok]}

(require '[reitit.core :as r])

(-> app (ring/get-router))
; #object[reitit.core$single_static_path_router]
```

* `:options` requests are served for all routes by default with 200 OK to better support things like [CORS](https://en.wikipedia.org/wiki/Cross-origin_resource_sharing)
  * the default handler is not documented in Swagger
  * new router option `:reitit.ring/default-options-handler` to change this behavior. Setting `nil` disables this.

* updated deps:

```clj
[ring/ring-core "1.7.0"] is available but we use "1.6.3"
```

### `reitit-http`

* `:options` requests are served for all routes by default with 200 OK to better support things like [CORS](https://en.wikipedia.org/wiki/Cross-origin_resource_sharing)
  * the default handler is not documented in Swagger
  * new router option `:reitit.http/default-options-handler` to change this behavior. Setting `nil` disables this.

### `reitit-middleware`

* fix `reitit.ring.middleware.parameters/parameters-middleware`

* updated deps:

```clj
[metosin/muuntaja "0.6.1"] is available but we use "0.6.0"
```

### `reitit-swagger-ui`

* updated deps:

```clj
[metosin/jsonista "0.2.2"] is available but we use "0.2.1"
```

## 0.2.2 (2018-09-09)

* better documentation for interceptors
* sample apps:
  * [Sieppari, reitit-http & swagger](https://github.com/metosin/reitit/blob/master/examples/http-swagger/src/example/server.clj)
  * [Pedestal, reitit-http & swagger](https://github.com/metosin/reitit/blob/master/examples/pedestal-swagger/src/example/server.clj)

### `reitit-middleware`

* new middleware `reitit.ring.middleware.parameters/parameters-middleware` to wrap query & form params.

### `reitit-interceptors`

* new module like `reitit-middleware` but for interceptors. See the [Docs](https://metosin.github.io/reitit/http/default_interceptors.html).

## 0.2.1 (2018-09-04)

### `reitit-schema`

* updated deps:

```clj
[metosin/schema-tools "0.10.4"] is available but we use "0.10.3"
```

### `reitit-middleware`

* updated deps:

```clj
[metosin/muuntaja "0.6.0"] is available but we use "0.6.0-alpha5"
```

## 0.2.0 (2018-09-03)

Sample apps demonstrating the current status of `reitit`:

* [`reitit-ring` with coercion, swagger and default middleware](https://github.com/metosin/reitit/blob/master/examples/ring-swagger/src/example/server.clj)
* [`reitit-frontend`, the easy way](https://github.com/metosin/reitit/blob/master/examples/frontend/src/frontend/core.cljs)
* [`reitit-frontent` with Keechma-style controllers](https://github.com/metosin/reitit/blob/master/examples/frontend-controllers/src/frontend/core.cljs)
* [`reitit-http` with Pedestal](https://github.com/metosin/reitit/blob/master/examples/pedestal/src/example/server.clj)
* [`reitit-http` with Sieppari](https://github.com/metosin/reitit/blob/master/examples/http/src/example/server.clj)

### `reitit-core`

* **BREAKING**: the router option key to extract body format has been renamed: `:extract-request-format` => `:reitit.coercion/extract-request-format`
  * should only concern you if you are not using [Muuntaja](https://github.com/metosin/muuntaja).
* the `r/routes` returns just the path + data tuples as documented, not the compiled route results. To get the compiled results, use `r/compiled-routes` instead.
* new [faster](https://github.com/metosin/reitit/blob/master/perf-test/clj/reitit/impl_perf_test.clj) and more correct encoders and decoders for query & path params.
  * all path-parameters are now decoded correctly with `reitit.impl/url-decode`, thanks to [Matthew Davidson](https://github.com/KingMob)!
  * query-parameters are encoded with `reitit.impl/form-encode`, so spaces are `+` instead of `%20`.
* correctly read `:header` params from request `:headers`, not `:header-params`
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

### `reitit-swagger`

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

### `reitit-middleware`

* A new module with common data-driven middleware: exception handling, content negotiation & multipart requests. See [the docs](https://metosin.github.io/reitit/ring/default_middleware.html).


### `reitit-swagger-ui`

* **BREAKING**: pass swagger-ui `:config` as-is (instead of mixed-casing keys) to swagger-ui, fixes [#109](https://github.com/metosin/reitit/issues/109):
  * see [docs](https://github.com/swagger-api/swagger-ui/tree/2.x#parameters) for available parameters.

```clj
(swagger-ui/create-swagger-ui-handler
  {:path "/"
   :url "/api/swagger.json"
   :config {:jsonEditor true
            :validatorUrl nil}})
```

### `reitit-frontend`

* new module for frontend-routing. See [docs](https://metosin.github.io/reitit/frontend/basics.html) for details.

## 0.1.3 (2018-6-25)

### `reitit-core`

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
