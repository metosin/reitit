# Clojure.spec Coercion

The [clojure.spec](https://clojure.org/guides/spec) library specifies the structure of data, validates or destructures it, and can generate data based on the spec.

## Warning

`clojure.spec` by itself doesn't support coercion. `reitit` uses [spec-tools](https://github.com/metosin/spec-tools) that adds coercion to spec. Like `clojure.spec`, it's alpha as it leans on `clojure.spec.alpha/conform`, which is concidered a spec internal, that might be changed or removed later.

For now, all leaf specs need to be wrapped into [Spec Records](https://github.com/metosin/spec-tools/blob/master/README.md#spec-records) to get the coercion working.

There are [CLJ-2116](https://dev.clojure.org/jira/browse/CLJ-2116) and [CLJ-2251](https://dev.clojure.org/jira/browse/CLJ-2251) that would help solve this elegantly.

## Example

```clj
(require '[reitit.coercion.spec])
(require '[reitit.coercion :as coercion])
(require '[spec-tools.spec :as spec])
(require '[clojure.spec.alpha :as s])
(require '[reitit.core :as r])

;; need to wrap the primitives!
(s/def ::company spec/string?)
(s/def ::user-id spec/int?)
(s/def ::path-params (s/keys :req-un [::company ::user-id]))

(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.spec/coercion
                                 :parameters {:path ::path-params}}]
    {:compile coercion/compile-request-coercers}))

(defn match-by-path-and-coerce! [path]
  (if-let [match (r/match-by-path router path)]
    (assoc match :parameters (coercion/coerce! match))))
```

Successful coercion:

```clj
(match-by-path-and-coerce! "/metosin/users/123")
; #Match{:template "/:company/users/:user-id",
;        :data {:name :user/user-view,
;               :coercion <<:spec>>
;               :parameters {:path ::path-params}},
;        :result {:path #object[reitit.coercion$request_coercer$]},
;        :path-params {:company "metosin", :user-id "123"},
;        :parameters {:path {:company "metosin", :user-id 123}}
;        :path "/metosin/users/123"}
```

Failing coercion:

```clj
(match-by-path-and-coerce! "/metosin/users/ikitommi")
; => ExceptionInfo Request coercion failed...
```
