# Composing Routers

Data-driven approach in `reitit` allows us to compose routes, route data, route specs, middleware and interceptors chains. We can compose routers too. This is needed to achieve dynamic routing like in [Compojure](https://github.com/weavejester/compojure).

## Immutability

Once a router is created, the routing tree is immutable and cannot be changed. To change the routing, we need to create a new router with changed routes and/or options. For this, the `Router` protocol exposes it's resolved routes via `r/routes` and options via `r/options`.

## Adding routes

Let's create a router:

```clj
(require '[reitit.core :as r])

(def router
  (r/router
    [["/foo" ::foo]
     ["/bar/:id" ::bar]]))
```

We can query the resolved routes and options:

```clj
(r/routes router)
;[["/foo" {:name :user/foo}]
; ["/bar/:id" {:name :user/bar}]]

(r/options router)
;{:lookup #object[...]
; :expand #object[...]
; :coerce #object[...]
; :compile #object[...]
; :conflicts #object[...]}
```

Let's add a helper function to create a new router with extra routes:

```clj
(defn add-routes [router routes]
  (r/router
    (into (r/routes router) routes)
    (r/options router)))
```

We can now create a new router with extra routes:

```clj
(def router2
  (add-routes
    router
    [["/baz/:id/:subid" ::baz]]))

(r/routes router2)
;[["/foo" {:name :user/foo}]
; ["/bar/:id" {:name :user/bar}]
; ["/baz/:id/:subid" {:name :user/baz}]]
```

The original router was not changed:

```clj
(r/routes router)
;[["/foo" {:name :user/foo}]
; ["/bar/:id" {:name :user/bar}]]
```

When a new router is created, all rules are applied, including the conflict resolution:

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

Let's create a helper function to merge routers:

```clj
(defn merge-routers [& routers]
  (r/router
    (apply merge (map r/routes routers))
    (apply merge (map r/options routers))))
```

We can now merge multiple routers into one:

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

Routers can be nested using the catch-all parameter.

Here's a router with deeply nested routers under a `:router` key in the route data:

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

That didn't work as we wanted, as the nested routers don't have such a route. The core routing doesn't understand anything the `:router` key, so it only matched against the top-level router, which gave a match for the catch-all path.

As the `Match` contains all the route data, we can create a new matching function that understands the `:router` key. Below is a function that does recursive matching using the subrouters. It returns either `nil` or a vector of matches.

```clj
(require '[clojure.string :as str])

(defn recursive-match-by-path [router path]
  (if-let [match (r/match-by-path router path)]
    (if-let [subrouter (-> match :data :router)]
      (let [subpath (subs path (str/last-index-of (:template match) "/"))]
        (if-let [submatch (recursive-match-by-path subrouter subpath)]
          (cons match submatch)))
      (list match))))
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

Let's create a helper to get only the route names for matches:

```clj
(defn name-path [router path]
  (some->> (recursive-match-by-path router path)
           (mapv (comp :name :data))))

(name-path router "/olipa/kerran/avaruus")
; [:olipa :kerran :avaruus]
```

So, we can nest routers, but why would we do that?

## Dynamic routing

In all the examples above, the routers were created ahead of time, making the whole route tree effectively static.  To have more dynamic routing, we can use router references allowing the router to be swapped over time. We can also create fully dynamic routers where the router is re-created for each request. Let's walk through both cases.

First, we need to modify our matching function to support router references:

```clj
(defn- << [x] 
  (if (instance? clojure.lang.IDeref x) 
    (deref x) x))

(defn recursive-match-by-path [router path]
  (if-let [match (r/match-by-path (<< router) path)]
    (if-let [subrouter (-> match :data :router <<)]
      (let [subpath (subs path (str/last-index-of (:template match) "/"))]
        (if-let [submatch (recursive-match-by-path subrouter subpath)]
          (cons match submatch)))
      (list match))))
```

Then, we need some routers.

First, a reference to a router that can be updated on background, for example when a new entry in inserted into a database. We'll wrap the router into a `atom`:

```clj
(def beer-router
  (atom
    (r/router 
      [["/lager" :lager]])))
```

Second, a reference to router, which is re-created on each routing request:

```clj
(def dynamic-router
  (reify clojure.lang.IDeref
    (deref [_]
      (r/router
        ["/duo" (keyword (str "duo" (rand-int 100)))]))))
```

We can compose the routers into a system-level static root router:

```clj
(def router
  (r/router
    [["/gin/napue" :napue]
     ["/ciders/*" :ciders]
     ["/beers/*" {:name :beers
                  :router beer-router}]
     ["/dynamic/*" {:name :dynamic
                    :router dynamic-router}]]))
```

Matching root routes:

```clj
(name-path router "/vodka/russian")
; nil

(name-path router "/gin/napue")
; [:napue]
```

Matching (nested) beer routes:

```clj
(name-path router "/beers/lager")
; [:beers :lager]

(name-path router "/beers/saison")
; nil
```

No saison!? Let's add the route:

```clj
(swap! beer-router add-routes [["/saison" :saison]])
```

There we have it:

```clj
(name-path router "/beers/saison")
; [:beers :saison]
```

We can't add conflicting routes:

```clj
(swap! beer-router add-routes [["/saison" :saison]])
;CompilerException clojure.lang.ExceptionInfo: Router contains conflicting route paths:
;
;   /saison
;-> /saison
```

The dynamic routes are re-created on every request:

```clj
(name-path router "/dynamic/duo")
; [:dynamic :duo71]

(name-path router "/dynamic/duo")
; [:dynamic :duo55]
```

### Performance

With nested routers, instead of having to do just one route match, matching is recursive, which adds a small cost. All nested routers need to be of type catch-all at top-level, which is order of magnitude slower than fully static routes. Dynamic routes are the slowest ones, at least two orders of magnitude slower, as the router needs to be recreated for each request.

A quick benchmark on the recursive lookups:

| path             | time    | type
|------------------|---------|-----------------------
| `/gin/napue`     | 40ns    | static
| `/ciders/weston` | 440ns   | catch-all
| `/beers/saison`  | 600ns   | catch-all + static
| `/dynamic/duo`   | 12000ns | catch-all + dynamic

The non-recursive lookup for `/gin/napue` is around 23ns.

Comparing the dynamic routing performance with Compojure:

```clj
(require '[compojure.core :refer [context])

(def app
  (context "/dynamic" [] (constantly :duo)))

(app {:uri "/dynamic/duo" :request-method :get})
; :duo
```

| path             | time    | type
|------------------|---------|-----------------------
| `/dynamic/duo`   | 20000ns | compojure

Can we make the nester routing faster? Sure. We could use the Router `:compile` hook to compile the nested routers for better performance. We could also allow router creation rules to be disabled, to get the dynamic routing much faster.

### When to use nested routers?

Nesting routers is not trivial and because of that, should be avoided. For dynamic (request-time) route generation, it's the only choice. For other cases, nested routes are most likely a better option.

Let's re-create the previous example with normal route nesting/composition.

A helper to the root router:

```clj
(defn create-router [beers]
  (r/router
    [["/gin/napue" :napue]
     ["/ciders/*" :ciders]
     ["/beers" (for [beer beers]
                 [(str "/" beer) (keyword "beer" beer)])]
     ["/dynamic/*" {:name :dynamic
                    :router dynamic-router}]]))
```

New new root router *reference* and a helper to reset it:

```clj
(def router
  (atom (create-router nil)))

(defn reset-router! [beers]
  (reset! router (create-router beers)))
```

The routing tree:

```clj
(r/routes @router)
;[["/gin/napue" {:name :napue}]
; ["/ciders/*" {:name :ciders}]
; ["/dynamic/*" {:name :dynamic,
;                :router #object[user$reify__24359]}]]
```

Let's reset the router with some beers:

```clj
(reset-router! ["lager" "sahti" "bock"])
```

We can see that the beer routes are now embedded into the core router:

```clj
(r/routes @router)
;[["/gin/napue" {:name :napue}]
; ["/ciders/*" {:name :ciders}]
; ["/beers/lager" {:name :beer/lager}]
; ["/beers/sahti" {:name :beer/sahti}]
; ["/beers/bock" {:name :beer/bock}]
; ["/dynamic/*" {:name :dynamic,
;                :router #object[user$reify__24359]}]]
```

And the routing works:

```clj
(name-path @router "/beers/sahti")
;[:beer/sahti]
```

All the beer-routes now match in constant time.

| path            | time    | type
|-----------------|---------|-----------------------
| `/beers/sahti`  | 40ns    | static

## TODO

* add an example how to do dynamic routing with `reitit-ring`
* maybe create a `recursive-router` into a separate ns with all `Router` functions implemented correctly? maybe not...
* add `reitit.core/merge-routes` to effectively merge routes with route data

