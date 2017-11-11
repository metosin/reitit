# Compiling Middleware

The [dynamic extensions](dynamic_extensions.md) is a easy way to extend the system. To enable fast lookups into route data, we can compile them into any shape (records, functions etc.) we want, enabling fast access at request-time.

Still, we can do much better. As we know the exact route that middleware/interceptor is linked to, we can pass the (compiled) route information into the middleware/interceptor at creation-time. It can do local reasoning: extract and transform relevant data just for it and pass it into the actual request-handler via a closure - yielding much faster runtime processing. It can also decide not to mount itself by returning `nil`. Why mount a `wrap-enforce-roles` middleware for a route if there are no roles required for it?

To enable this we use [middleware records](data_driven_middleware.md) `:gen-wrap` key instead of the normal `:wrap`. `:gen-wrap` expects a function of `route-meta router-opts => ?wrap`.

To demonstrate the two approaches, below are response coercion middleware written as normal ring middleware function and as middleware record with `:gen-wrap`. Actual codes can be found in [`reitit.ring.coercion`](https://github.com/metosin/reitit/blob/master/src/reitit/ring/coercion.cljc):

## Naive

* Reads the compiled route information on every request.

```clj
(defn wrap-coerce-response
  "Pluggable response coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :responses from route meta, otherwise will do nothing."
  [handler]
  (fn
    ([request]
     (let [response (handler request)
           method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :meta :responses)
           coercion (-> match :meta :coercion)
           opts (-> match :meta :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)]
           (coerce-response coercers request response))
         response)))
    ([request respond raise]
     (let [method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :meta :responses)
           coercion (-> match :meta :coercion)
           opts (-> match :meta :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)]
           (handler request #(respond (coerce-response coercers request %))))
         (handler request respond raise))))))
```

## Compiled

* Route information is provided via a closure
* Pre-compiled coercers
* Mounts only if `:coercion` and `:responses` are defined for the route

```clj
(require '[reitit.ring.middleware :as middleware])

(def gen-wrap-coerce-response
  "Generator for pluggable response coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :responses from route meta, otherwise does not mount."
  (middleware/create
    {:name ::coerce-response
     :gen-wrap (fn [{:keys [responses coercion opts]} _]
                (if (and coercion responses)
                  (let [coercers (response-coercers coercion responses opts)]
                    (fn [handler]
                      (fn
                        ([request]
                         (coerce-response coercers request (handler request)))
                        ([request respond raise]
                         (handler request #(respond (coerce-response coercers request %)) raise)))))))}))
```

The latter has 50% less code, is easier to reason about and is much faster.
