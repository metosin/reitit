# Compiling Middleware

The [dynamic extensions](dynamic_extensions.md) is a easy way to extend the system. To enable fast lookups into route data, we can compile them into any shape (records, functions etc.) we want, enabling fast access at request-time.

But, we can do much better. As we know the exact route that middleware/interceptor is linked to, we can pass the (compiled) route information into the middleware at creation-time. It can do local reasoning: extract and transform relevant data just for it and pass the optimized data into the actual request-handler via a closure - yielding much faster runtime processing. Middleware can also decide not to mount itself by returning `nil`. Why mount a `wrap-enforce-roles` middleware for a route if there are no roles required for it?

To enable this we use [middleware records](data_driven_middleware.md) `:compile` key instead of the normal `:wrap`. `:compile` expects a function of `route-data router-opts => ?IntoMiddleware`.

To demonstrate the two approaches, below are response coercion middleware written as normal ring middleware function and as middleware record with `:compile`.

## Normal Middleware

* Reads the compiled route information on every request. Everything is done at request-time.

```clj
(defn wrap-coerce-response
  "Middleware for pluggable response coercion.
  Expects a :coercion of type `reitit.coercion/Coercion`
  and :responses from route data, otherwise will do nothing."
  [handler]
  (fn
    ([request]
     (let [response (handler request)
           method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :data :responses)
           coercion (-> match :data :coercion)
           opts (-> match :data :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)]
           (coerce-response coercers request response))
         response)))
    ([request respond raise]
     (let [method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :data :responses)
           coercion (-> match :data :coercion)
           opts (-> match :data :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)]
           (handler request #(respond (coerce-response coercers request %))))
         (handler request respond raise))))))
```

## Compiled Middleware

* Route information is provided at creation-time
* Coercers are compiled at creation-time
* Middleware mounts only if `:coercion` and `:responses` are defined for the route
* Also defines spec for the route data `:responses` for the [route data validation](route_data_validation.md).

```clj
(require '[reitit.spec :as rs])

(def coerce-response-middleware
  "Middleware for pluggable response coercion.
  Expects a :coercion of type `reitit.coercion/Coercion`
  and :responses from route data, otherwise does not mount."
  {:name ::coerce-response
   :spec ::rs/responses
   :compile (fn [{:keys [coercion responses]} opts]
              (if (and coercion responses)
                (let [coercers (coercion/response-coercers coercion responses opts)]
                  (fn [handler]
                    (fn
                      ([request]
                       (coercion/coerce-response coercers request (handler request)))
                      ([request respond raise]
                       (handler request #(respond (coercion/coerce-response coercers request %)) raise)))))))})
```

It has 50% less code, it's much easier to reason about and is much faster.

### Require Keys on Routes at Creation Time

Often it is useful to require a route to provide a specific key.

```clj
(require '[buddy.auth.accessrules :as accessrules])

(s/def ::authorize
  (s/or :handler :accessrules/handler :rule :accessrules/rule))

(def authorization-middleware
  {:name ::authorization
   :spec (s/keys :req-un [::authorize])
   :compile
   (fn [route-data _opts]
     (when-let [rule (:authorize route-data)]
       (fn [handler]
         (accessrules/wrap-access-rules handler {:rules [rule]}))))})
```

In the example above the `:spec` expresses that each route is required to provide the `:authorize` key. However, in this case the compile function returns `nil` when that key is missing, which means **the middleware will not be mounted, the spec will not be considered, and the compiler will not enforce this requirement as intended**.

If you just want to enforce the spec return a map without `:wrap` or `:compile` keys, e.g. an empty map, `{}`.


```clj
(def authorization-middleware
  {:name ::authorization
   :spec (s/keys :req-un [::authorize])
   :compile
   (fn [route-data _opts]
     (if-let [rule (:authorize route-data)]
       (fn [handler]
         (accessrules/wrap-access-rules handler {:rules [rule]}))
       ;; return empty map just to enforce spec
       {}))})
```

The middleware (and associated spec) will still be part of the chain, but will not process the request.
