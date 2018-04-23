# Static Resources (Clojure Only)

Static resources can be served with a help of `reitit.ring/create-resource-handler`. It takes optionally an options map and returns a ring handler to serve files from Classpath. It returns `java.io.File` instances, so ring adapters can use NIO to effective Stream the files.

There are two options to serve the files.

## Internal routes

This is good option if static files can be from non-conflicting paths, e.g. `"/assets/*"`.


```clj
(require '[reitit.ring :as ring])

(ring/ring-handler
  (ring/router
    [["/ping" (constantly {:status 200, :body "pong"})]
     ["/assets/*" (ring/create-resource-handler)]])
  (ring/create-default-handler))
```

To serve static files with conflicting routes, e.g. `"/*#`, one needs to disable the confligt resolution:

```clj
(require '[reitit.ring :as ring])

(ring/ring-handler
  (ring/router
    [["/ping" (constantly {:status 200, :body "pong"})]
     ["/*" (ring/create-resource-handler)]]
    {:conflicts (constantly nil)})
  (ring/create-default-handler))
```

## External routes

To serve files from conflicting paths, e.g. `"/*"`, one option is to mount them to default-handler branch of `ring-handler`. This way, they are only served if none of the actual routes have matched.


```clj
(ring/ring-handler
  (ring/router
    ["/ping" (constantly {:status 200, :body "pong"})])
  (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler)))
```

## Configuration

`reitit.ring/create-resource-handler` takes optionally an options map to configure how the files are being served.

| key          | description |
| -------------|-------------|
| :parameter   | optional name of the wildcard parameter, defaults to unnamed keyword `:`
| :root        | optional resource root, defaults to `"public"`
| :mime-types  | optional extension->mime-type mapping, defaults to `reitit.ring.mime/default-types`
| :path        | optional path to mount the handler to. Works only if mounted outside of a router.

### TODO

* support for things like `:cache`, `:last-modified?` and `:index-files`

## Performance

Thanks to NIO-support, serving files is quite fast. With late2015 Macbook PRO and `[ikitommi/immutant "3.0.0-alpha1"]` here are some numbers:

##### Small file (17 bytes)

```
wrk -t2 -c100 -d2s http://localhost:3000/files/hello.json
34055 requests/sec
4.64MB / sec
```

##### large file (406kB)

```
wrk -t2 -c10 -d10s http://localhost:3000/files/image.jpg
2798 request/sec
1.08GB / sec
```

##### single huge file (775Mb)

```
wget http://localhost:3000/files/LilaBali2.pptx
315 MB/s
```
