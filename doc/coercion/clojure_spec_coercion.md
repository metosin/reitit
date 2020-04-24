# Clojure.spec Coercion

The [clojure.spec](https://clojure.org/guides/spec) library specifies the structure of data, validates or destructures it, and can generate data based on the spec.

## Warning

`clojure.spec` by itself doesn't support coercion. `reitit` uses [spec-tools](https://github.com/metosin/spec-tools) that adds coercion to spec. Like `clojure.spec`, it's alpha as it leans both on spec walking and `clojure.spec.alpha/conform`, which is considered a spec internal, that might be changed or removed later.

## Usage

For simple specs (core predicates, `spec-tools.core/spec`, `s/and`, `s/or`, `s/coll-of`, `s/keys`, `s/map-of`, `s/nillable` and `s/every`), the transformation is inferred using [spec-walker](https://github.com/metosin/spec-tools#spec-walker) and is automatic. To support all specs (like regex-specs), specs need to be wrapped into [Spec Records](https://github.com/metosin/spec-tools/blob/master/README.md#spec-records).

There are [CLJ-2116](https://dev.clojure.org/jira/browse/CLJ-2116) and [CLJ-2251](https://dev.clojure.org/jira/browse/CLJ-2251) that would help solve this elegantly. Go vote 'em up.

## Example

```clj
(require '[reitit.coercion.spec])
(require '[reitit.coercion :as coercion])
(require '[spec-tools.spec :as spec])
(require '[clojure.spec.alpha :as s])
(require '[reitit.core :as r])

;; simple specs, inferred
(s/def ::company string?)
(s/def ::user-id int?)
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

## Deeply nested

Spec-tools allow deeply nested specs to be coerced. One can test the coercion easily in the REPL.

Define some specs:

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.core :as st])

(s/def :sku/id keyword?)
(s/def ::sku (s/keys :req-un [:sku/id]))
(s/def ::skus (s/coll-of ::sku :into []))

(s/def :photo/id int?)
(s/def ::photo (s/keys :req-un [:photo/id]))
(s/def ::photos (s/coll-of ::photo :into []))

(s/def ::my-json-api (s/keys :req-un [::skus ::photos]))
```

Apply a string->edn coercion to the data:

```clj
(st/coerce
  ::my-json-api
  {:skus [{:id "123"}]
   :photos [{:id "123"}]}
  st/string-transformer)
; {:skus [{:id :123}]
;  :photos [{:id 123}]}
```

Apply a json->edn coercion to the data:

```clj
(st/coerce
  ::my-json-api
  {:skus [{:id "123"}]
   :photos [{:id "123"}]}
  st/json-transformer)
; {:skus [{:id :123}]
;  :photos [{:id "123"}]}
```

By default, reitit uses custom transformers that also strip out extra keys from `s/keys` specs:

```clj
(require '[reitit.coercion.spec :as rcs])

(st/coerce
  ::my-json-api
  {:TOO "MUCH"
   :skus [{:id "123"
           :INFOR "MATION"}]
   :photos [{:id "123"
             :HERE "TOO"}]}
  rcs/json-transformer)
; {:skus [{:id :123}]
;  :photos [{:id "123"}]}
```

## Defining Optional Keys

Going back to the previous example.

Suppose you want the `::my-json-api` to have optional `remarks` as string and each `photo` to have an optional `height` and `width` as integer.
The `s/keys` accepts `:opt-un` to support optional keys.

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.core :as st])

(s/def :sku/id keyword?)
(s/def ::sku (s/keys :req-un [:sku/id]))
(s/def ::skus (s/coll-of ::sku :into []))
(s/def ::remarks string?)  ;; define remarks as string

(s/def :photo/id int?)
(s/def :photo/height int?) ;; define height as int
(s/def :photo/width int?)  ;; define width as int
(s/def ::photo (s/keys :req-un [:photo/id]
                       :opt-un [:photo/height :photo/width])) ;; height and width are in :opt-un
(s/def ::photos (s/coll-of ::photo :into []))

(s/def ::my-json-api (s/keys :req-un [::skus ::photos]
                             :opt-un [::remarks])) ;; remarks is in the :opt-un
```

Apply a string->edn coercion to the data:

```clj
;; Omit optional keys
(st/coerce
  ::my-json-api
  {:skus [{:id "123"}]
   :photos [{:id "123"}]}
  st/string-transformer)
;;{:skus [{:id :123}],
;; :photos [{:id 123}]}


;; coerce the optional keys if present

(st/coerce
  ::my-json-api
  {:skus [{:id "123"}]
   :photos [{:id "123" :height "100" :width "100"}]
   :remarks "some remarks"}
  st/string-transformer)

;; {:skus [{:id :123}]
;;  :photos [{:id 123 :height 100 :width 100}]
;;  :remarks "some remarks"}

(st/coerce
  ::my-json-api
  {:skus [{:id "123"}]
   :photos [{:id "123" :height "100"}]}
  st/string-transformer)
;; {:skus [{:id :123}],
;;  :photos [{:id 123, :height 100}]}
```
