# Data-spec Coercion

[Data-specs](https://github.com/metosin/spec-tools#data-specs) is alternative, macro-free syntax to define `clojure.spec`s. As a bonus, supports the [runtime transformations via conforming](https://clojure.atlassian.net/browse/CLJ-2116) out-of-the-box.

```clj
(require '[reitit.coercion.spec])
(require '[reitit.coercion :as coercion])
(require '[reitit.core :as r])

(def router
  (r/router
    ["/:company/users/:user-id" {:name :user/user-view
                                 :coercion reitit.coercion.spec/coercion
                                 :parameters {:path {:company string?
                                                     :user-id int?}}}]
    {:compile coercion/compile-request-coercers}))

(defn match-by-path-and-coerce! [path]
  (if-let [match (r/match-by-path router path)]
    (assoc match :parameters (coercion/coerce! match))))
```

Successful coercion:

```clj
(match-by-path-and-coerce! "/metosin/users/123")
;; => {:template "/:company/users/:user-id",
;;     :data {:name :user/user-view,
;;            :coercion ...
;;            :parameters ...}
;;     :path-params {:company "metosin", :user-id "123"},
;;     :parameters {:path {:company "metosin", :user-id 123}}}
```

Failing coercion:

```clj
(match-by-path-and-coerce! "/metosin/users/ikitommi")
;; =thrown-match=> {:type :reitit.coercion/request-coercion, :problems ...}
```
