# Dev Worklfow

Many applications will require the routes to span multiple namespaces. It is quite easy to do so with reitit, but we might hit a problem during developement.

## An example

Consider this sample routing :

```clj
(ns ns1)

(def routes
  ["/bar" ::bar])

(ns ns2)
(require '[ns1])

(def routes
  [["/ping" ::ping]
   ["/more" ns1/routes]])

(ns ns3)
(require '[ns1])
(require '[ns2])
(require '[reitit.core :as r])

(def routes
  ["/api"
   ["/ns2" ns2/routes]
   ["/ping" ::ping]])

(def router (r/router routes))
```

We may query the top router and get the expected result :
```clj
(r/match-by-path router "/api/ns2/more/bar")
;#reitit.core.Match{:template "/api/ns2/more/bar", :data {:name :ns1/bar}, :result nil, :path-params {}, :path "/api/ns2/more/bar"}
```

Notice the route name : ```:ns1/bar```

When we change the routes in ```ns1``` like this :
```clj
(ns ns1
  (:require [reitit.core :as r]))
  
(def routes
  ["/bar" ::bar-with-new-name])
```

After we recompile the ```ns1``` namespace, and query again
```clj
ns1/routes
;["/bar" :ns1/bar-with-new-name]
;The routes var in ns1 was changed indeed

(r/match-by-path router "/api/ns2/more/bar")
;#reitit.core.Match{:template "/api/ns2/more/bar", :data {:name :ns1/bar}, :result nil, :path-params {}, :path "/api/ns2/more/bar"}
```

The route name is still ```:ns1/bar``` !

While we could use the [reloaded workflow](http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded) to reload the whole routing tree, it is not always possible, and quite frankly a bit slower than we might want for fast iterations.

## A crude solution

In order to see the changes without reloading the whole route tree, we can use functions.

```clj
(ns ns1)

(defn routes [] ;; Now a function !
  ["/bar" ::bar])

(ns ns2)
(require '[ns1])

(defn routes [] ;; Now a function !
  [["/ping" ::ping]
   ["/more" (ns1/routes)]]) ;; Now a function call

(ns ns3)
(require '[ns1])
(require '[ns2])
(require '[reitit.core :as r])

(defn routes [] ;; Now a function !
  ["/api"
   ["/ns2" (ns2/routes)] ;; Now a function call
   ["/ping" ::ping]])

(def router #(r/router (routes))) ;; Now a function
```

Let's query again

```clj
(r/match-by-path (router) "/api/ns2/more/bar") 
;#reitit.core.Match{:template "/api/ns2/more/bar", :data {:name :ns1/bar}, :result nil, :path-params {}, :path "/api/ns2/more/bar"}
```

Notice that's we're now calling a function rather than just passing ```router``` to the matching function.

Now let's again change the route name in ```ns1```, and recompile that namespace.

```clj
(ns ns1)

(defn routes [] 
  ["/bar" ::bar-with-new-name])
```

let's see the query result :

```clj
(r/match-by-path (router) "/api/ns2/more/bar")
;#reitit.core.Match{:template "/api/ns2/more/bar", :data {:name :ns1/bar-with-new-name}, :result nil, :path-params {}, :path "/api/ns2/more/bar"}
```

Notice that the name is now correct, without reloading every namespace under the sun.

## Why is this a crude solution ?

The astute reader will have noticed that we're recompiling the full routing tree on every invocation. While this solution is practical during developement, it goes contrary to the performance goals of reitit. 

We need a way to only do this once at production time.

## An easy fix

Let's apply a small change to our ```ns3```. We'll replace our router by two different routers, one for dev and one for production.

```clj
(ns ns3)
(require '[ns1])
(require '[ns2])
(require '[reitit.core :as r])

(defn routes [] 
  ["/api"
   ["/ns2" (ns2/routes)] 
   ["/ping" ::ping]])

(def dev-router #(r/router (routes))) ;; A router for dev
(def prod-router (constantly (r/router (routes)))) ;; A router for prod 
```

And there you have it, dynamic during dev, performance at production. We have it all !

