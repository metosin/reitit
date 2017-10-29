# Compiling Middleware

The [meta-data extensions](ring.md#meta-data-based-extensions) are a easy way to extend the system. Routes meta-data can be transformed into any shape (records, functions etc.) in route compilation, enabling fast access at request-time.

Still, we can do better. As we know the exact route that interceptor/middleware is linked to, we can pass the (compiled) route information into the interceptor/middleware at creation-time. It can extract and transform relevant data just for it and pass it into the actual request-handler via a closure - yielding faster runtime processing.

To do this we use [middleware records](ring.md#middleware-records) `:gen` hook instead of the normal `:wrap`. `:gen` expects a function of `route-meta router-opts => wrap`. Middleware can also return `nil`, which effective unmounts the middleware. Why mount a `wrap-enforce-roles` middleware for a route if there are no roles required for it?

To demonstrate the two approaches, below are response coercion middleware written as normal ring middleware function and as middleware record with `:gen`. These are the actual codes are from [`reitit.coercion`](https://github.com/metosin/reitit/blob/master/src/reitit/coercion.cljc):

## Naive

* Extracts the compiled route information on every request.

```clj
(defn wrap-coerce-response
  "Pluggable response coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :responses from route meta, otherwise does not mount."
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
         (let [coercers (response-coercers coercion responses opts)
               coerced (coerce-response coercers request response)]
           (coerce-response coercers request (handler request)))
         (handler request))))
    ([request respond raise]
     (let [response (handler request)
           method (:request-method request)
           match (ring/get-match request)
           responses (-> match :result method :meta :responses)
           coercion (-> match :meta :coercion)
           opts (-> match :meta :opts)]
       (if (and coercion responses)
         (let [coercers (response-coercers coercion responses opts)
               coerced (coerce-response coercers request response)]
           (handler request #(respond (coerce-response coercers request %))))
         (handler request respond raise))))))
```

## Compiled

* Route information is provided via a closure
* Pre-compiled coercers
* Mounts only if `:coercion` and `:responses` are defined for the route

```clj
(def gen-wrap-coerce-response
  "Generator for pluggable response coercion middleware.
  Expects a :coercion of type `reitit.coercion.protocol/Coercion`
  and :responses from route meta, otherwise does not mount."
  (middleware/create
    {:name ::coerce-response
     :gen (fn [{:keys [responses coercion opts]} _]
            (if (and coercion responses)
              (let [coercers (response-coercers coercion responses opts)]
                (fn [handler]
                  (fn
                    ([request]
                     (coerce-response coercers request (handler request)))
                    ([request respond raise]
                     (handler request #(respond (coerce-response coercers request %)) raise)))))))}))
```

The `:gen` -version has 50% less code, is easier to reason about and is 2-4x faster on basic perf tests.
