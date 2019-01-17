# Clojure.spec Coercion

The [clojure.spec](https://clojure.org/guides/spec) library specifies the structure of data, validates or destructures it, and can generate data based on the spec.

## Warning

`clojure.spec` by itself doesn't support coercion. `reitit` uses [spec-tools](https://github.com/metosin/spec-tools) that adds coercion to spec. Like `clojure.spec`, it's alpha as it leans both on spec walking and `clojure.spec.alpha/conform`, which is concidered a spec internal, that might be changed or removed later.

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

## Error printing

Spec problems are exposed as-is into request & response coercion errors, enabling pretty-printers like expound to be used:

```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.middleware.exception :as exception])
(require '[reitit.ring.coercion :as coercion])
(require '[expound.alpha :as expound])

(defn coercion-error-handler [status]
  (let [printer (expound/custom-printer {:theme :figwheel-theme, :print-specs? false})
        handler (exception/create-coercion-handler status)]
    (fn [exception request]
      (printer (-> exception ex-data :problems))
      (handler exception request))))

(def app
  (ring/ring-handler
    (ring/router
      ["/plus"
       {:get
        {:parameters {:query {:x int?, :y int?}}
         :responses {200 {:body {:total pos-int?}}}
         :handler (fn [{{{:keys [x y]} :query} :parameters}]
                    {:status 200, :body {:total (+ x y)}})}}]
      {:data {:coercion reitit.coercion.spec/coercion
              :middleware [(exception/create-exception-middleware
                             (merge
                               exception/default-handlers
                               {:reitit.coercion/request-coercion (coercion-error-handler 400)
                                :reitit.coercion/response-coercion (coercion-error-handler 500)}))
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware]}})))

(app
  {:uri "/plus"
   :request-method :get
   :query-params {"x" "1", "y" "fail"}})
; => ...
; -- Spec failed --------------------
;
;   {:x ..., :y "fail"}
;                ^^^^^^
;
; should satisfy
;
;   int?



(app
  {:uri "/plus"
   :request-method :get
   :query-params {"x" "1", "y" "-2"}})
; => ...
;-- Spec failed --------------------
;
;   {:total -1}
;           ^^
;
; should satisfy
;
;   pos-int?
```
