# Static Resources (Clojure Only)

Static resources can be served by using the following two functions:

* `reitit.ring/create-resource-handler`, which returns a Ring handler that serves files from classpath, and
* `reitit.ring/create-file-handler`, which returns a Ring handler that servers files from file system

There are two ways to mount the handlers.
The examples below use `reitit.ring/create-resource-handler`, but `reitit.ring/create-file-handler` works the same way.

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

To serve static files with conflicting routes, e.g. `"/*"`, one needs to disable the conflict resolution:

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

A better way to serve files from conflicting paths, e.g. `"/*"`, is to serve them from the default-handler.
One can compose multiple default locations using `reitit.ring/ring-handler`.
This way, they are only served if none of the actual routes have matched.

```clj
(ring/ring-handler
  (ring/router
    ["/ping" (constantly {:status 200, :body "pong"})])
  (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler)))
```

## Configuration

`reitit.ring/create-file-handler` and `reitit.ring/create-resource-handler` take optionally an options map to configure how the files are being served.

| key                | description |
| -------------------|-------------|
| :parameter         | optional name of the wildcard parameter, defaults to unnamed keyword `:`
| :root              | optional resource root, defaults to `\"public\"`
| :path              | path to mount the handler to. Required when mounted outside of a router, does not work inside a router.
| :loader            | optional class loader to resolve the resources
| :index-files       | optional vector of index-files to look in a resource directory, defaults to `[\"index.html\"]`
| :index-redirect?   | optional boolean: if true (default), redirect to index file, if false serve it directly
| :not-found-handler | optional handler function to use if the requested resource is missing (404 Not Found)


### TODO

* support for things like `:cache`, `:etag`, `:last-modified?`, and `:gzip`
* support for ClojureScript
