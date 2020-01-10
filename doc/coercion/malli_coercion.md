# Malli Coercion

[Malli](https://github.com/metosin/malli) is data-driven Schema library for Clojure/Script.

```clj
(require '[reitit.coercion.malli])
(require '[reitit.coercion :as coercion])
(require '[reitit.core :as r])

(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.malli/coercion
                                 :parameters {:path [:map
                                                     [:company string?]
                                                     [:user-id int?]]}]
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
;               :coercion <<:malli>>
;               :parameters {:path [:map
;                                   [:company string?]
;                                   [:user-id int?]]}},
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
