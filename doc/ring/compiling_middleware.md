# Compiling Middleware

The [dynamic extensions](dynamic_extensions.md) is a easy way to extend the system. To enable fast lookups into route data, we can compile them into any shape (records, functions etc.) we want, enabling fast access at request-time.

But, we can do much better. As we know the exact route that middleware/interceptor is linked to, we can pass the (compiled) route information into the middleware/interceptor at creation-time. It can do local reasoning: extract and transform relevant data just for it and pass it into the actual request-handler via a closure - yielding much faster runtime processing. It can also decide not to mount itself by returning `nil`. Why mount a `wrap-enforce-roles` middleware for a route if there are no roles required for it?

To enable this we use [middleware records](data_driven_middleware.md) `:compile` key instead of the normal `:wrap`. `:compile` expects a function of `route-data router-opts => ?wrap`.

To demonstrate the two approaches, below are response coercion middleware written as normal ring middleware function and as middleware record with `:compile`.

## Normal Middleware

* Reads the compiled route information on every request.

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

* Route information is provided via a closure
* Pre-compiled coercers
* Mounts only if `:coercion` and `:responses` are defined for the route
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

The latter has 50% less code, is easier to reason about and is much faster.
