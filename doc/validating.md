# Validating route-trees

Namespace `reitit.spec` contains [specs](https://clojure.org/about/spec) for routes, router and router options.

To enable spec-validation of `router` inputs & outputs at development time, one can do the following:

```clj
; add to dependencies:
; [expound "0.3.0"]

(require '[clojure.spec.test.alpha :as st])
(require '[expound.alpha :as expound])
(require '[clojure.spec.alpha :as s])
(require '[reitit.spec])

(st/instrument `reitit/router)
(set! s/*explain-out* expound/printer)

(reitit/router
  ["/api"
   ["/publuc"
    ["/ping"]
    ["pong"]]])
; -- Spec failed --------------------
;
; ["/api" ...]
;  ^^^^^^
;
;     should satisfy
;
; (clojure.spec.alpha/cat
;   :path
;   :reitit.spec/path
;   :arg
;   (clojure.spec.alpha/? :reitit.spec/arg)
;   :childs
;   (clojure.spec.alpha/* (clojure.spec.alpha/and :reitit.spec/raw-route)))
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
;   (clojure.spec.alpha/* (clojure.spec.alpha/and :reitit.spec/raw-route)))
; :reitit.spec/raw-routes:
; (clojure.spec.alpha/or
;   :route
;   :reitit.spec/raw-route
;   :routes
;   (clojure.spec.alpha/coll-of :reitit.spec/raw-route :into []))
;
; -- Spec failed --------------------
;
; [... [... ... ["pong"]]]
;                ^^^^^^
;
;     should satisfy
;
; (fn [%] (clojure.string/starts-with? % "/"))
;
; -- Relevant specs -------
;
; :reitit.spec/path:
; (clojure.spec.alpha/and
;   clojure.core/string?
;   (clojure.core/fn [%] (clojure.string/starts-with? % "/")))
; :reitit.spec/raw-route:
; (clojure.spec.alpha/cat
;   :path
;   :reitit.spec/path
;   :arg
;   (clojure.spec.alpha/? :reitit.spec/arg)
;   :childs
;   (clojure.spec.alpha/* (clojure.spec.alpha/and :reitit.spec/raw-route)))
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

### Validating meta-data

*TODO*
