# Composing Routers

Routers expose both their routes and options via the `Router` protocol, enabling one to create new routers from existing ones.

## Adding routes to an existing routers

Let's define a router in an `Atom`:

```clj
(require '[reitit.core :as r])

(def router (atom (r/router
                    [["/foo/bar" identity]
                     ["/foo/bar/:id" identity]])))

(r/routes @router)
;[["/foo/bar" {:handler #object[clojure.core$identity]}]
; ["/foo/bar/:id" {:handler #object[clojure.core$identity]}]]
```

A helper to add new route to a router:

```clj
(defn add-route [router route]
  (r/router
    (conj (r/routes router) route)
    (r/options router)))
```

Now, we can add routers to the router:

```clj
(swap! router add-route ["/foo/bar/:id/:subid" identity])

(r/routes @router)
;[["/foo/bar" {:handler #object[clojure.core$identity]}]
; ["/foo/bar/:id" {:handler #object[clojure.core$identity]}]
; ["/foo/bar/:id/:subid" {:handler #object[clojure.core$identity]}]]
```

Router is recreated, so all the rules are fired:

```clj
(swap! router add-route ["/foo/:fail" identity])
;CompilerException clojure.lang.ExceptionInfo: Router contains conflicting routes:
;
;   /foo/bar
;-> /foo/:fail
```

## Merging routers

Given we have two routers:

```clj
(def r1 (r/router ["/route1" identity]))
(def r2 (r/router ["/route2" identity]))
```

We can create a new router, with merged routes and options:

```clj
(def r12 (r/router
           (merge
             (r/routes r1)
             (r/routes r2))
           (merge
             (r/options r1)
             (r/options r2))))

(r/routes r12)
;[["/route1" {:handler #object[clojure.core$identity]}]
; ["/route2" {:handler #object[clojure.core$identity]}]]
```

## TODO

* `reitit.core/merge-routes` to effectively merge routes with route data

