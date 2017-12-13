# Clojure.spec Coercion

The [clojure.spec](https://clojure.org/guides/spec) library specifies the structure of data, validates or destructures it, and can generate data based on the spec.

**NOTE**: Currently, `clojure.spec` [doesn't support runtime transformations via conforming](https://dev.clojure.org/jira/browse/CLJ-2116), so one needs to wrap all specs into [Spec Records](https://github.com/metosin/spec-tools/blob/master/README.md#spec-records) to get the coercion working.

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
;        :params {:company "metosin", :user-id "123"},
;        :parameters {:path {:company "metosin", :user-id 123}}
;        :path "/metosin/users/123"}
```

Failing coercion:

```clj
(match-by-path-and-coerce! "/metosin/users/ikitommi")
; => ExceptionInfo Request coercion failed...
```
