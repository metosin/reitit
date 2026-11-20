# Plumatic Schema Coercion

[Plumatic Schema](https://github.com/plumatic/schema) is a Clojure(Script) library for declarative data description and validation.

```clj
(require '[reitit.coercion.schema])
(require '[reitit.coercion :as coercion])
(require '[schema.core :as s])
(require '[reitit.core :as r])

(def router
  (r/router
    ["/:company/users/:user-id" {:name :user/user-view
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:company s/Str
                                                     :user-id s/Int}}}]
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
;;     :result ...,
;;     :path-params {:company "metosin", :user-id "123"},
;;     :parameters {:path {:company "metosin", :user-id 123}}
;;     :path "/metosin/users/123"}
```

Failing coercion:

```clj
(match-by-path-and-coerce! "/metosin/users/ikitommi")
;; =thrown-match=> {:type :reitit.coercion/request-coercion}
```
