# Route validation

Namespace `reitit.spec` contains [clojure.spec](https://clojure.org/about/spec) definitions for raw-routes, routes, router and router options.

## Example

```clj
(require '[clojure.spec.alpha :as s])
(require '[reitit.spec :as spec])

(def routes-from-db
  ["tenant1" ::tenant1])

(s/valid? ::spec/raw-routes routes-from-db)
; false

(s/explain ::spec/raw-routes routes-from-db)
; In: [0] val: "tenant1" fails spec: :reitit.spec/path at: [:route :path] predicate: (or (blank? %) (starts-with? % "/"))
; In: [0] val: "tenant1" fails spec: :reitit.spec/raw-route at: [:routes] predicate: (cat :path :reitit.spec/path :arg (? :reitit.spec/arg) :childs (* (and (nilable :reitit.spec/raw-route))))
; In: [1] val: :user/tenant1 fails spec: :reitit.spec/raw-route at: [:routes] predicate: (cat :path :reitit.spec/path :arg (? :reitit.spec/arg) :childs (* (and (nilable :reitit.spec/raw-route))))
; :clojure.spec.alpha/spec  :reitit.spec/raw-routes
; :clojure.spec.alpha/value  ["tenant1" :user/tenant1]
```

## At development time

`reitit.core/router` can be instrumented and use a tool like [expound](https://github.com/bhb/expound) to pretty-print the spec problems.

First add a `:dev` dependency to:

```clj
[expound "0.4.0"] ; or higher
```

Some bootstrapping:

```clj
(require '[clojure.spec.test.alpha :as stest])
(require '[expound.alpha :as expound])
(require '[clojure.spec.alpha :as s])
(require '[reitit.spec])

(stest/instrument `reitit/router)
(set! s/*explain-out* expound/printer)
```

And we are ready to go:

```clj
(require '[reitit.core :as r])

(r/router
  ["/api"
   ["/public"
    ["/ping"]
    ["pong"]]])

; CompilerException clojure.lang.ExceptionInfo: Call to #'reitit.core/router did not conform to spec:
;
; -- Spec failed --------------------
;
; Function arguments
;
; (["/api" ...])
;   ^^^^^^
;
;     should satisfy
;
; (clojure.spec.alpha/cat
;   :path
;   :reitit.spec/path
;   :arg
;   (clojure.spec.alpha/? :reitit.spec/arg)
;   :childs
;   (clojure.spec.alpha/*
;     (clojure.spec.alpha/and
;       (clojure.spec.alpha/nilable :reitit.spec/raw-route))))
;
; or
;
; (clojure.spec.alpha/cat
;   :path
;   :reitit.spec/path
;   :arg
;   (clojure.spec.alpha/? :reitit.spec/arg)
;   :childs
;   (clojure.spec.alpha/*
;     (clojure.spec.alpha/and
;       (clojure.spec.alpha/nilable :reitit.spec/raw-route))))
;
; -- Relevant specs -------
;
; :reitit.spec/raw-route:
; (clojure.spec.alpha/cat
;   :path
;   :reitit.spec/path
;   :arg
;   (clojure.spec.alpha/? :reitit.spec/arg)
;   :childs
;   (clojure.spec.alpha/*
;     (clojure.spec.alpha/and
;       (clojure.spec.alpha/nilable :reitit.spec/raw-route))))
; :reitit.spec/raw-routes:
; (clojure.spec.alpha/or
;   :route
;   :reitit.spec/raw-route
;   :routes
;   (clojure.spec.alpha/coll-of :reitit.spec/raw-route :into []))
;
; -- Spec failed --------------------
;
; Function arguments
;
; ([... [... ... ["pong"]]])
;                 ^^^^^^
;
;     should satisfy
;
; (fn
;   [%]
;   (or
;     (clojure.string/blank? %)
;     (clojure.string/starts-with? % "/")))
;
; or
;
; (fn
;   [%]
;   (or
;     (clojure.string/blank? %)
;     (clojure.string/starts-with? % "/")))
;
; -- Relevant specs -------
;
; :reitit.spec/path:
; (clojure.spec.alpha/and
;   clojure.core/string?
;   (clojure.core/fn
;     [%]
;     (clojure.core/or
;       (clojure.string/blank? %)
;       (clojure.string/starts-with? % "/"))))
; :reitit.spec/raw-route:
; (clojure.spec.alpha/cat
;   :path
;   :reitit.spec/path
;   :arg
;   (clojure.spec.alpha/? :reitit.spec/arg)
;   :childs
;   (clojure.spec.alpha/*
;     (clojure.spec.alpha/and
;       (clojure.spec.alpha/nilable :reitit.spec/raw-route))))
; :reitit.spec/raw-routes:
; (clojure.spec.alpha/or
;   :route
;   :reitit.spec/raw-route
;   :routes
;   (clojure.spec.alpha/coll-of :reitit.spec/raw-route :into []))
;
; -------------------------
; Detected 2 errors
```
