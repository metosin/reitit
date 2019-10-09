# Frequently Asked Questions

* [Why yet another routing library?](#why-yet-another-routing-library)
* [How can I contribute?](#how-can-i-contribute)
* [How does Reitit differ from Bidi?](#how-does-reitit-differ-from-bidi)
* [How does Reitit differ from Pedestal?](#how-does-reitit-differ-from-pedestal)
* [How does Reitit differ from Compojure?](#how-does-reitit-differ-from-compojure)
* [How do you pronounce "reitit"?](#how-do-you-pronounce-reitit)

### Why yet another routing library?

Routing and dispatching is in the core of most business apps, so we should have a great library to for it. There are already many good routing libs for Clojure, but we felt none was perfect. So, we took best parts of existing libs and added features that were missing: first-class composable route data, full route conflict resolution and pluggable coercion. Goal was to make a data-driven library that works, is fun to use and is really, really fast.

### How can I contribute?

You can join [#reitit](https://clojurians.slack.com/messages/reitit/) channel in [Clojurians slack](http://clojurians.net/) to discuss things. Known roadmap is mostly written in [issues](https://github.com/metosin/reitit/issues).

### How does Reitit differ from Bidi?

[Bidi](https://github.com/juxt/bidi) is an great and proven library for ClojureScript and we have been using it in many of our frontend projects. Both Reitit and Bidi are data-driven, bi-directional and work with both Clojure & ClojureScript. Here are the main differences:

#### Route syntax

* Bidi supports multiple representations for route syntax, Reitit supports just one (simple) syntax.
* Bidi uses special (Clojure) syntax for route patterns while Reitit separates (human-readable) paths strings from route data - still exposing the machine-readable syntax for extensions.

Bidi:

```clj
(def routes
  ["/" [["auth/login" :auth/login]
        [["auth/recovery/token/" :token] :auth/recovery]
        ["workspace/" [[[:project-uuid "/" :page-uuid] :workspace/page]]]]])
```

Reitit:

```clj
(def routes
  [["/auth/login" :auth/login]
   ["/auth/recovery/token/:token" :auth/recovery]
   ["/workspace/:project-uuid/:page-uuid" :workspace/page]])
```

#### Features

* Bidi has extra features like route guards
* Reitit ships with composable route data, specs, full route conflict resolution and pluggable coercion.

#### Performance

* Bidi is not optimized for speed and thus, Reitit is [much faster](performance.md) than Bidi. From Bidi source:

```clj
;; Route compilation was only marginally effective and hard to
;; debug. When bidi matching takes in the order of 30 micro-seconds,
;; this is good enough in relation to the time taken to process the
;; overall request.
```

### How does Reitit differ from Pedestal?

[Pedestal](http://pedestal.io/) is an great and proven library and has had great influence in Reitit. Both Reitit and Pedestal are data-driven and provide bi-directional routing and fast. Here are the main differences:

#### ClojureScript

* Pedestal targets only Clojure, while Reitit works also with ClojureScript.

#### Route syntax

* Pedestal supports multiple representations for route syntax: terse, table and verbose. Reitit provides only one representation.
* Pedestal supports both maps or keyword-arguments in route data, in Reitit, it's all maps.

Pedestal:

```clj
["/api/ping" :get identity :route-name ::ping]
```

Reitit:

```clj
["/api/ping" {:get identity, :name ::ping}]
```

#### Features

* Pedestal supports route guards
* Pedestal supports interceptors (`reitit-http` module will support them too).
* Reitit ships with composable route data, specs, full route conflict resolution and pluggable coercion.
* In Pedestal, different routers [behave differently](https://github.com/pedestal/pedestal/issues/532), in Reitit, all work the same.

#### Performance

Reitit routing was originally based on Pedestal Routing an thus they same similar performance. For routing trees with both static and wildcard routes, Reitit is much faster thanks to it's `mixed-router` algorithm.

### How does Reitit differ from Compojure?

[Compojure](https://github.com/weavejester/compojure) is the most used routing library in Clojure. It's proven and awesome.

#### ClojureScript

* Compojure targets only Clojure, while Reitit works also with ClojureScript.

#### Route syntax

* Compojure uses routing functions and macros while reitit is all data
* Compojure allows easy destructuring of route params on mid-path
* Applying middleware for sub-paths is hacky on Compojure, `reitit-ring` resolves this with data-driven middleware

Compojure:

```clj
(defroutes routes
  (wrap-routes
    (context "/api" []
      (GET "/users/:id" [id :<< as-int]
        (ok (get-user id)))
      (POST "/pizza" []
        (wrap-log post-pizza-handler)))
    wrap-api :secure))
```

`reitit-ring` with `reitit-spec` module:

```clj
(def routes
  ["/api" {:middleware [[wrap-api :secure]]}
   ["/users/:id" {:get {:parameters {:path {:id int?}}}
                  :handler (fn [{:keys [parameters]}]
                             (ok (get-user (-> parameters :body :id))))}
    ["/pizza" {:post {:middleware [wrap-log]
                      :handler post-pizza-handler}]]])
```

#### Features

* Dynamic routing is trivial in Compojure, with reitit, some trickery is needed
* Reitit ships with composable route data, specs, full route conflict resolution and pluggable coercion.

#### Performance

Reitit is [much faster](performance.md) than Compojure.

### How do you pronounce "reitit"?

[Google Translate does a decent job pronouncing it](https://translate.google.com/#view=home&op=translate&sl=fi&tl=en&text=reitit) (click the speaker icon on the left). The English expression *rate it* is a good approximation.
