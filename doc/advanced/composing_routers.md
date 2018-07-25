# Composing Routers

Once a router is created, the routing tree is immutable and cannot be changed. To modify the routes, we have to make a new copy of the router, with modified routes and/or options. For this, the `Router` exposes the resolved routes via `r/routes` and options via `r/options`.

## Adding routes

Let's create a router:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/foo" ::foo]
     ["/bar/:id" ::bar]]))
```

It's resolved routes and options:

```clj
(r/routes router)
;[["/foo" {:name :user/foo}]
; ["/bar/:idr" {:name :user/bar}]]

(r/options router)
;{:lookup #object[...]
; :expand #object[...]
; :coerce #object[...]
; :compile #object[...]
; :conflicts #object[...]}
```

A helper to create a new router with extra routes:

```clj
(defn add-routes [router routes]
  (r/router
    (into (r/routes router) routes)
    (r/options router)))
```

New router with an extra route:

```clj
(def router2
  (add-routes
    router
    [["/baz/:id/:subid" ::baz]]))

(r/routes router2)
;[["/foo" {:name :user/bar}]
; ["/bar/:id" {:name :user/bar}]
; ["/baz/:id/:subid" {:name :user/baz}]]
```

All rules are applied, including the conflict resolution:

```clj
(add-routes
  router2
  [["/:this/should/:fail" ::fail]])
;CompilerException clojure.lang.ExceptionInfo: Router contains conflicting route paths:
;
;   /baz/:id/:subid
;-> /:this/should/:fail
```

## Merging routers

A helper to merge routers:

```clj
(defn merge-routers [& routers]
  (r/router
    (apply merge (map r/routes routers))
    (apply merge (map r/options routers))))
```

Merging three routers into one:

```clj
(def router
  (merge-routers
    (r/router ["/route1" ::route1])
    (r/router ["/route2" ::route2])
    (r/router ["/route3" ::route3])))

(r/routes router)
;[["/route1" {:name :user/route1}]
; ["/route2" {:name :user/route2}]
; ["/route3" {:name :user/route3}]]
```

## Nesting routers

Routers can be nested too, using the catch-all parameter.

A router with nested routers using a custom `:router` key:

```clj
(def router
  (r/router
    [["/ping" :ping]
     ["/olipa/*" {:name :olipa
                  :router (r/router
                            [["/olut" :olut]
                             ["/makkara" :makkara]
                             ["/kerran/*" {:name :kerran
                                           :router (r/router
                                                     [["/avaruus" :avaruus]
                                                      ["/ihminen" :ihminen]])}]])}]]))
```

Matching by path:

```clj
(r/match-by-path router "/olipa/kerran/iso/kala")
;#Match{:template "/olipa/*"
;       :data {:name :olipa
;              :router #object[reitit.core$mixed_router]}
;       :result nil
;       :path-params {: "kerran/iso/kala"}
;       :path "/olipa/iso/kala"}
```

That not right, it should not have matched. The core routing doesn't understand anything about nesting, so it only matched against the top-level router, which gave a match for the catch-all path. 

As the `Match` contains the route data, we can create a new matching function that understands our custom `:router` syntax. Here is a function that does recursive matching using the subrouters. It returns either `nil` or a vector of mathces.

```clj
(require '[clojure.string :as str])

(defn recursive-match-by-path [router path]
  (if-let [match (r/match-by-path router path)]
    (if-let [subrouter (-> match :data :router)]
      (if-let [submatch (recursive-match-by-path subrouter (subs path (str/last-index-of (:template match) "/")))]
        (into [match] submatch))
      [match])))
```

With invalid nested path we get now `nil` as expected:

```clj
(recursive-match-by-path router "/olipa/kerran/iso/kala")
; nil
```

With valid path we get all the nested matches:

```clj
(recursive-match-by-path router "/olipa/kerran/avaruus")
;[#reitit.core.Match{:template "/olipa/*"
;                    :data {:name :olipa
;                           :router #object[reitit.core$mixed_router]}
;                    :result nil
;                    :path-params {: "kerran/avaruus"}
;                    :path "/olipa/kerran/avaruus"}
; #reitit.core.Match{:template "/kerran/*"
;                    :data {:name :kerran
;                           :router #object[reitit.core$lookup_router]}
;                    :result nil
;                    :path-params {: "avaruus"}
;                    :path "/kerran/avaruus"}
; #reitit.core.Match{:template "/avaruus" 
;                    :data {:name :avaruus} 
;                    :result nil 
;                    :path-params {} 
;                    :path "/avaruus"}]
```

Helper to get only the route names for matches:

```clj
(defn name-path [router path]
  (some->> (recursive-match-by-path router path)
           (mapv (comp :name :data))))

(name-path router "/olipa/kerran/avaruus")
; [:olipa :kerran :avaruus]
```

## Dynamic routing

In all the examples above, the routers were created ahead of time, making the whole route tree effective static. To do dynamic routing, we should use router references so that we can update the routes either on background or per request basis. Let's walk through both cases.

First, we need to modify our matching function to support router references:

```clj
(defn- << [x]
  (if (instance? clojure.lang.IDeref x) (deref x) x))

(defn recursive-match-by-path [router path]
  (if-let [match (r/match-by-path (<< router) path)]
    (if-let [subrouter (-> match :data :router <<)]
      (if-let [submatch (recursive-match-by-path subrouter (subs path (str/last-index-of (:template match) "/")))]
        (into [match] submatch))
      [match])))
```

A router that can be updated on demand, for example based on a domain event when a new entry in inserted into a database. We'll wrap the router into a `atom` to achieve this.

```clj
(def beer-router
  (atom
    (r/router 
      [["/lager" :lager]])))
```

Another router, which is re-created on each routing request.

```clj
(def dynamic-router
  (reify clojure.lang.IDeref
    (deref [_]
      (r/router
        ["/duo" (keyword (gensym ""))]))))
```

Now we can compose the routers into a system-level static root router.

```clj
(def router
  (r/router
    [["/gin/napue" :napue]
     ["/ciders/*" :ciders]
     ["/beers/*" {:name :beers
                  :router beer-router}]
     ["/dynamic/*" {:name :other
                    :router dynamic-router}]]))
```

Matching root routes:

```clj
(name-path "/vodka/russian")
; nil

(name-path "/gin/napue")
; [:napue]
```

Matching (nested) beer routes:

```clj
(name-path "/beers/lager")
; [:beers :lager]

(name-path "/beers/saison")
; nil
```

No saison!? Let's add the route:

```clj
(swap! beer-router add-routes [["/saison" :saison]])
```

There we have it:

```clj
(name-path "/beers/saison")
; [:beers :saison]
```

We can't add a conflicting routes:

```clj
(swap! beer-router add-routes [["/saison" :saison]])
;CompilerException clojure.lang.ExceptionInfo: Router contains conflicting route paths:
;
;   /saison
;-> /saison
```

The dynamic routes are re-created on every request:

```clj
(name-path "/dynamic/duo")
; [:other :2390883]

(name-path "/dynamic/duo")
; [:other :2390893]
```

### Performance

With nested routers, instead of having to do just one route match, matching is recursive, which adds a small cost. All nested routers need to be of type catch-all at top-level, which is order of magnitude slower than fully static routes. Dynamic routes are the slowest ones, at least an order of magnitude slower, as the router needs to be recreated for each request.

Here's a quick benchmark on the recursive matches.

| path             | time    | type
|------------------|---------|-----------------------
| `/gin/napue`     | 40ns    | static
| `/ciders/weston` | 440ns   | catch-all
| `/beers/saison`  | 600ns   | catch-all + static
| `/dynamic/duo`   | 12000ns | catch-all + dynamic

In this example, we could have wrapped the top-level router in an `atom` and add the beer-routes directly to it, making them order of magnitude faster.

## TODO

* example how to do dynamic routing with `reitit-ring`
* create a `recursive-router` into a separate ns with all `r/routes` implemented correctly?
* `reitit.core/merge-routes` to effectively merge routes with route data

