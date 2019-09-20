# Route Data Validation

Ring route validation works [just like with core router](../basics/route_data_validation.md), with few differences:

* `reitit.ring.spec/validate` should be used instead of `reitit.spec/validate` - to support validating all endpoints (`:get`, `:post` etc.)
* With `clojure.spec` validation, Middleware can contribute to route spec via `:specs` key. The effective route data spec is router spec merged with middleware specs.

## Example

A simple app with spec-validation turned on:

```clj
(require '[clojure.spec.alpha :as s])
(require '[reitit.ring :as ring])
(require '[reitit.ring.spec :as rrs])
(require '[reitit.spec :as rs])
(require '[expound.alpha :as e])

(defn handler [_]
  {:status 200, :body "ok"})

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/public"
        ["/ping" {:get handler}]]
       ["/internal"
        ["/users" {:get {:handler handler}
                   :delete {:handler handler}}]]]
      {:validate rrs/validate
       ::rs/explain e/expound-str})))
```

All good:

```clj
(app {:request-method :get
      :uri "/api/internal/users"})
; {:status 200, :body "ok"}
```

### Explicit specs via middleware

Middleware that requires `:zone` to be present in route data:

```clj
(s/def ::zone #{:public :internal})

(def zone-middleware
  {:name ::zone-middleware
   :spec (s/keys :req-un [::zone])
   :wrap (fn [handler]
           (fn [request]
             (let [zone (-> request (ring/get-match) :data :zone)]
               (println zone)
               (handler request))))})
```

Missing route data fails fast at router creation:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [zone-middleware]} ;; <--- added
       ["/public"
        ["/ping" {:get handler}]]
       ["/internal"
        ["/users" {:get {:handler handler}
                   :delete {:handler handler}}]]]
      {:validate rrs/validate
       ::rs/explain e/expound-str})))
; CompilerException clojure.lang.ExceptionInfo: Invalid route data:
;
; -- On route -----------------------
;
; "/api/public/ping" :get
;
; -- Spec failed --------------------
;
; {:middleware ...,
;  :handler ...}
;
; should contain key: `:zone`
;
; |   key |  spec |
; |-------+-------|
; | :zone | :zone |
;
;
; -- On route -----------------------
;
; "/api/internal/users" :get
;
; -- Spec failed --------------------
;
; {:middleware ...,
;  :handler ...}
;
; should contain key: `:zone`
;
; |   key |  spec |
; |-------+-------|
; | :zone | :zone |
;
;
; -- On route -----------------------
;
; "/api/internal/users" :delete
;
; -- Spec failed --------------------
;
; {:middleware ...,
;  :handler ...}
;
; should contain key: `:zone`
;
; |   key |  spec |
; |-------+-------|
; | :zone | :zone |
```

Adding the `:zone` to route data fixes the problem:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [zone-middleware]}
       ["/public" {:zone :public} ;; <--- added
        ["/ping" {:get handler}]]
       ["/internal" {:zone :internal} ;; <--- added
        ["/users" {:get {:handler handler}
                   :delete {:handler handler}}]]]
      {:validate rrs/validate
       ::rs/explain e/expound-str})))

(app {:request-method :get
      :uri "/api/internal/users"})
; in zone :internal
; => {:status 200, :body "ok"}
```

### Implicit specs

By design, clojure.spec validates all fully-qualified keys with `s/keys` specs even if they are not defined in that keyset. Validation is implicit but powerful.

Let's reuse the `wrap-enforce-roles` from [Dynamic extensions](dynamic_extensions.md) and define specs for the data:

```clj
(require '[clojure.set :as set])

(s/def ::role #{:admin :manager})
(s/def ::roles (s/coll-of ::role :into #{}))

(defn wrap-enforce-roles [handler]
  (fn [{::keys [roles] :as request}]
    (let [required (some-> request (ring/get-match) :data ::roles)]
      (if (and (seq required) (not (set/subset? required roles)))
        {:status 403, :body "forbidden"}
        (handler request)))))
```

`wrap-enforce-roles` silently ignores if the `::roles` is not present:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [zone-middleware
                            wrap-enforce-roles]} ;; <--- added
       ["/public" {:zone :public}
        ["/ping" {:get handler}]]
       ["/internal" {:zone :internal}
        ["/users" {:get {:handler handler}
                   :delete {:handler handler}}]]]
      {:validate rrs/validate
       ::rs/explain e/expound-str})))

(app {:request-method :get
      :uri "/api/zones/admin/ping"})
; in zone :internal
; => {:status 200, :body "ok"}
```

But fails if they are present and invalid:

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api" {:middleware [zone-middleware
                            wrap-enforce-roles]}
       ["/public" {:zone :public}
        ["/ping" {:get handler}]]
       ["/internal" {:zone :internal}
        ["/users" {:get {:handler handler
                         ::roles #{:manager} ;; <--- added
                   :delete {:handler handler
                            ::roles #{:adminz}}}]]] ;; <--- added
      {:validate rrs/validate
       ::rs/explain e/expound-str})))
; CompilerException clojure.lang.ExceptionInfo: Invalid route data:
;
; -- On route -----------------------
;
; "/api/internal/users" :delete
;
; -- Spec failed --------------------
;
; {:middleware ...,
;  :zone ...,
;  :handler ...,
;  :user/roles #{:adminz}}
;                ^^^^^^^
;
; should be one of: `:admin`,`:manager`
```

### Pushing the data to the endpoints

Ability to define (and reuse) route-data in mid-paths is a powerful feature, but having data defined all around might be harder to reason about. There is always an option to define all data at the endpoints.

```clj
(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/public"
        ["/ping" {:zone :public
                  :get handler
                  :middleware [zone-middleware
                               wrap-enforce-roles]}]]
       ["/internal"
        ["/users" {:zone :internal
                   :middleware [zone-middleware
                                wrap-enforce-roles]
                   :get {:handler handler
                         ::roles #{:manager}}
                   :delete {:handler handler
                            ::roles #{:admin}}}]]]
      {:validate rrs/validate
       ::rs/explain e/expound-str})))
```

Or even flatten the routes:

```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/api/public/ping" {:zone :public
                            :get handler
                            :middleware [zone-middleware
                                         wrap-enforce-roles]}]
       ["/api/internal/users" {:zone :internal
                               :middleware [zone-middleware
                                            wrap-enforce-roles]
                               :get {:handler handler
                                     ::roles #{:manager}}
                               :delete {:handler handler
                                        ::roles #{:admin}}}]]
      {:validate rrs/validate
       ::rs/explain e/expound-str})))
```

The common Middleware can also be pushed to the router, here cleanly separating behavior and data:

```clj
(def app
  (ring/ring-handler
    (ring/router
      [["/api/public/ping" {:zone :public
                            :get handler}]
       ["/api/internal/users" {:zone :internal
                               :get {:handler handler
                                     ::roles #{:manager}}
                               :delete {:handler handler
                                        ::roles #{:admin}}}]]
      {:data {:middleware [zone-middleware wrap-enforce-roles]}
       :validate rrs/validate
       ::rs/explain e/expound-str})))
```
