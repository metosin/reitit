# Route Data Validation

Route data can be anything, so it's easy to do go wrong. Accidentally adding a `:role` key instead of `:roles` might hinder the whole routing app without any authorization in place.

To fail fast, we could use the custom `:coerce` and `:compile` hooks to apply data validation and throw exceptions on first sighted problem.

But there is a better way. Router has a `:validation` hook to validate the whole route tree after it's successfuly compiled. It expects a 2-arity function `routes opts => ()` that can side-effect in case of validation errors.

## clojure.spec

Namespace `reitit.spec` contains specs for main parts of `reitit.core` and a helper function `validate` that runs spec validation for all route data and throws an exception if any errors are found.

A Router with invalid route data:

```clj
(require '[reitit.core :as r])

(r/router
  ["/api" {:handler "identity"}])
; #object[reitit.core$...]
```

Fails fast with `clojure.spec` validation turned on:

```clj
(require '[reitit.spec :as rs])

(r/router
  ["/api" {:handler "identity"}]
  {:validate rs/validate})
; CompilerException clojure.lang.ExceptionInfo: Invalid route data:
;
; -- On route -----------------------
;
; "/api"
;
; In: [:handler] val: "identity" fails spec: :reitit.spec/handler at: [:handler] predicate: fn?
;
; {:problems (#reitit.spec.Problem{:path "/api", :scope nil, :data {:handler "identity"}, :spec :reitit.spec/default-data, :problems #:clojure.spec.alpha{:problems ({:path [:handler], :pred clojure.core/fn?, :val "identity", :via [:reitit.spec/default-data :reitit.spec/handler], :in [:handler]}), :spec :reitit.spec/default-data, :value {:handler "identity"}}})}, compiling: ...

```

### Customizing spec validation

`rs/validate` reads the following router options:

  | key            | description |
  | ---------------|-------------|
  | `:spec`        | the spec to verify the route data (default `::rs/default-data`)
  | `::rs/explain` | custom explain function (default `clojure.spec.alpha/explain-str`)

**NOTE**: `clojure.spec` implicitly validates all values with fully-qualified keys if specs exist with the same name.

Below is an example of using [expound](https://github.com/bhb/expound) to pretty-print route data problems.

```clj
(require '[clojure.spec.alpha :as s])
(require '[expound.alpha :as e])

(s/def ::role #{:admin :manager})
(s/def ::roles (s/coll-of ::role :into #{}))

(r/router
  ["/api" {:handler identity
           ::roles #{:adminz}}]
  {::rs/explain e/expound-str
   :validate rs/validate})
; CompilerException clojure.lang.ExceptionInfo: Invalid route data:
;
; -- On route -----------------------
;
; "/api"
;
; -- Spec failed --------------------
;
; {:handler ..., :user/roles #{:adminz}}
;                              ^^^^^^^
;
; should be one of: `:admin`,`:manager`
;
; -- Relevant specs -------
;
; :user/role:
; #{:admin :manager}
; :user/roles:
; (clojure.spec.alpha/coll-of :user/role :into #{})
; :reitit.spec/default-data:
; (clojure.spec.alpha/keys
;   :opt-un
;   [:reitit.spec/name :reitit.spec/handler])
;
; -------------------------
; Detected 1 error
;
; {:problems (#reitit.spec.Problem{:path "/api", :scope nil, :data {:handler #object[clojure.core$identity 0x15b59b0e "clojure.core$identity@15b59b0e"], :user/roles #{:adminz}}, :spec :reitit.spec/default-data, :problems #:clojure.spec.alpha{:problems ({:path [:user/roles], :pred #{:admin :manager}, :val :adminz, :via [:reitit.spec/default-data :user/roles :user/role], :in [:user/roles 0]}), :spec :reitit.spec/default-data, :value {:handler #object[clojure.core$identity 0x15b59b0e "clojure.core$identity@15b59b0e"], :user/roles #{:adminz}}}})}, compiling: ...
```

Explicitly requiring a `::roles` key in a route data:

```clj
(r/router
  ["/api" {:handler identity}]
  {:spec (s/merge (s/keys :req [::roles]) ::rs/default-data)
   ::rs/explain e/expound-str
   :validate rs/validate})
; CompilerException clojure.lang.ExceptionInfo: Invalid route data:
;
; -- On route -----------------------
;
; "/api"
;
; -- Spec failed --------------------
;
; {:handler
;  #object[clojure.core$identity 0x15b59b0e "clojure.core$identity@15b59b0e"]}
;
; should contain key: `:user/roles`
;
; |         key |                                   spec |
; |-------------+----------------------------------------|
; | :user/roles | (coll-of #{:admin :manager} :into #{}) |
;
;
;
; -------------------------
; Detected 1 error
;
; {:problems (#reitit.spec.Problem{:path "/api", :scope nil, :data {:handler #object[clojure.core$identity 0x15b59b0e "clojure.core$identity@15b59b0e"]}, :spec #object[clojure.spec.alpha$merge_spec_impl$reify__2124 0x7461744b "clojure.spec.alpha$merge_spec_impl$reify__2124@7461744b"], :problems #:clojure.spec.alpha{:problems ({:path [], :pred (clojure.core/fn [%] (clojure.core/contains? % :user/roles)), :val {:handler #object[clojure.core$identity 0x15b59b0e "clojure.core$identity@15b59b0e"]}, :via [], :in []}), :spec #object[clojure.spec.alpha$merge_spec_impl$reify__2124 0x7461744b "clojure.spec.alpha$merge_spec_impl$reify__2124@7461744b"], :value {:handler #object[clojure.core$identity 0x15b59b0e "clojure.core$identity@15b59b0e"]}}})}, compiling:(/Users/tommi/projects/metosin/reitit/test/cljc/reitit/spec_test.cljc:151:1)
```
