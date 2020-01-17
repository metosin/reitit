# Content Negotiation

Wrapper for [Muuntaja](https://github.com/metosin/muuntaja) middleware for content-negotiation, request decoding and response encoding. Takes explicit configuration via `:muuntaja` key in route data. Emit's [swagger](swagger.md) `:produces` and `:consumes` definitions automatically based on the Muuntaja configuration.

Negotiates a request body based on `Content-Type` header and response body based on `Accept`, `Accept-Charset` headers. Publishes the negotiation results as `:muuntaja/request` and `:muuntaja/response` keys into the request.

Decodes the request body into `:body-params` using the `:muuntaja/request` key in request if the `:body-params` doesn't already exist.

Encodes the response body using the `:muuntaja/response` key in request if the response doesn't have `Content-Type` header already set.

Expected route data:

| key          | description |
| -------------|-------------|
| `:muuntaja`  | `muuntaja.core/Muuntaja` instance, does not mount if not set.

```clj
(require '[reitit.ring.middleware.muuntaja :as muuntaja])
```

* `muuntaja/format-middleware` - Negotiation, request decoding and response encoding in a single Middleware
* `muuntaja/format-negotiate-middleware` - Negotiation
* `muuntaja/format-request-middleware` - Request decoding
* `muuntaja/format-response-middleware` - Response encoding

```clj
(require '[reitit.ring :as ring])
(require '[reitit.ring.coercion :as rrc])
(require '[reitit.coercion.spec :as rcs])
(require '[ring.adapter.jetty :as jetty])
(require '[muuntaja.core :as m])

(def app
  (ring/ring-handler
    (ring/router
      [["/math"
        {:post {:summary "negotiated request & response (json, edn, transit)"
                :parameters {:body {:x int?, :y int?}}
                :responses {200 {:body {:total int?}}}
                :handler (fn [{{{:keys [x y]} :body} :parameters}]
                           {:status 200
                            :body {:total (+ x y)}})}}]
       ["/xml"
        {:get {:summary "forced xml response"
               :handler (fn [_]
                          {:status 200
                           :headers {"Content-Type" "text/xml"}
                           :body "<kikka>kukka</kikka>"})}}]]
      {:data {:muuntaja m/instance
              :coercion rcs/coercion
              :middleware [muuntaja/format-middleware
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))

(jetty/run-jetty #'app {:port 3000, :join? false})
```

Testing with [httpie](https://httpie.org/):

```bash
> http POST :3000/math x:=1 y:=2

HTTP/1.1 200 OK
Content-Length: 11
Content-Type: application/json; charset=utf-8
Date: Wed, 22 Aug 2018 16:59:54 GMT
Server: Jetty(9.2.21.v20170120)

{
 "total": 3
}
```

```bash
> http :3000/xml

HTTP/1.1 200 OK
Content-Length: 20
Content-Type: text/xml
Date: Wed, 22 Aug 2018 16:59:58 GMT
Server: Jetty(9.2.21.v20170120)

<kikka>kukka</kikka>
```


## Changing default parameters

The current JSON formatter used by `reitit` already have the option to parse keys as `keyword` which is a sane default in Clojure. However, if you would like to parse all the `double` as `bigdecimal` you'd need to change an option of the [JSON formatter](https://github.com/metosin/jsonista)


```clj
(def new-muuntaja-instance
  (m/create
   (assoc-in
    m/default-options
    [:formats "application/json" :decoder-opts :bigdecimals]
    true)))

```

Now you should change the `m/instance` installed in the router with the `new-muuntaja-instance`.

You can find more options for [JSON](https://cljdoc.org/d/metosin/jsonista/0.2.5/api/jsonista.core#object-mapper) and [EDN].


## Adding custom encoder

The example below is from `muuntaja` explaining how to add a custom encoder to parse a `java.util.Date` instance.

```clj

(def muuntaja-instance
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/json" :encoder-opts]
      {:date-format "yyyy-MM-dd"})))

(->> {:value (java.util.Date.)}
     (m/encode m "application/json")
     slurp)
; => "{\"value\":\"2019-10-15\"}"

```

## Adding all together

If you inspect `m/default-options` it's only a map, therefore you can compose your new muuntaja instance with as many options as you need it.

```clj
(def new-muuntaja
  (m/create
   (-> m/default-options
       (assoc-in [:formats "application/json" :decoder-opts :bigdecimals] true)
       (assoc-in [:formats "application/json" :encoder-opts :data-format] "yyyy-MM-dd"))))
```
