# Malli Coercion

[Malli](https://github.com/metosin/malli) is data-driven Schema library for Clojure/Script.

## Default Syntax

By default, [Vector Syntax](https://github.com/metosin/malli#vector-syntax) is used:

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
                                                     [:user-id int?]]}}]
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

## Lite Syntax

Same using [Lite Syntax](https://github.com/metosin/malli#lite):

```clj
(def router
  (r/router
    ["/:company/users/:user-id" {:name ::user-view
                                 :coercion reitit.coercion.malli/coercion
                                 :parameters {:path {:company string?
                                                     :user-id int?}}}]
    {:compile coercion/compile-request-coercers}))
```

## Configuring coercion

Using `create` with options to create the coercion instead of `coercion`:

```clj
(require '[malli.util :as mu])

(reitit.coercion.malli/create
  {:transformers {:body {:default reitit.coercion.malli/default-transformer-provider
                         :formats {"application/json" reitit.coercion.malli/json-transformer-provider}}
                  :string {:default reitit.coercion.malli/string-transformer-provider}
                  :response {:default reitit.coercion.malli/default-transformer-provider
                             :formats {"application/json" reitit.coercion.malli/json-transformer-provider}}}
   ;; set of keys to include in error messages
   :error-keys #{:type :coercion :in #_:schema :value #_:errors :humanized #_:transformed}
   ;; support lite syntax?
   :lite true
   ;; schema identity function (default: close all map schemas)
   :compile mu/closed-schema
   ;; validate request & response
   :validate true
   ;; top-level short-circuit to disable request & response coercion
   :enabled true
   ;; strip-extra-keys (affects only predefined transformers)
   :strip-extra-keys true
   ;; add/set default values
   ;; Can be false, true or a map of options to pass to malli.transform/default-value-transformer,
   ;; for example {:malli.transform/add-optional-keys true}
   :default-values true
   ;; encode-error
   :encode-error nil
   ;; malli options
   :options nil})
```
