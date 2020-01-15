# Format Negotiation

`reitit` uses [muuntaja](https://github.com/metosin/muuntaja) as a library to handle format negotiation, encoding and decoding.

In order to have access to the options below, you need the following namespaces:

```clj
(require '[muuntaja.core :as m])
```

Then, a `muuntaja` instance must be installed in the router to your coercion benefit from the customizations.

```clj
(ring/ring-handler
   (ring/router
    ;; your route definitions
    ;; ....
    :data {:coercion reitit.coercion.spec/coercion
           :muuntaja m/instance}})
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
