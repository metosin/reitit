# Controllers

* https://github.com/metosin/reitit/tree/master/examples/frontend-controllers

Controllers run code when a route is entered and left. This can be useful to:

- Load resources
- Update application state

## How controllers work

A controller map can contain these properties:

* `identity` function which takes a Match and returns an arbitrary value,
* or `parameters` value, which declares which parameters should affect
controller identity
* `start` & `stop` functions, which are called with controller identity

When you navigate to a route that has a controller, controller identity
is first resolved by using `parameters` declaration, or by calling `identity` function,
or if neither is set, the identity is `nil`. Next, the controller
is initialized by calling `start` with the controller identity value.
When you exit that route, `stop` is called with the last controller identity value.

If you navigate to the same route with different match, identity gets
resolved again. If the identity changes from the previous value, controller
is reinitialized: `stop` and `start` get called again.

You can add controllers to a route by adding them to the route data in the
`:controllers` vector. For example:

```cljs
["/item/:id"
 {:controllers [{:parameters {:path [:id]}
                 :start  (fn [parameters] (js/console.log :start (-> parameters :path :id)))
                 :stop   (fn [parameters] (js/console.log :stop (-> parameters :path :id)))}]}]
```

You can leave out `start` or `stop` if you do not need both of them.

## Enabling controllers

You need to
call
[`reitit.frontend.controllers/apply-controllers`](https://cljdoc.org/d/metosin/reitit-frontend/CURRENT/api/reitit.frontend.controllers#apply-controllers) whenever
the URL changes. You can call it from the `on-navigate` callback of
`reitit.frontend.easy`:

```cljs
(ns frontend.core
  (:require [reitit.frontend.easy :as rfe]
            [reitit.frontend.controllers :as rfc]))

(defonce match-a (atom nil))

(def routes
  ["/" ...])

(defn init! []
  (rfe/start!
    routes
    (fn [new-match]
      (swap! match-a
        (fn [old-match]
          (when new-match
            (assoc new-match
              :controllers (rfc/apply-controllers (:controllers old-match) new-match))))))))
```

See also [the full example](https://github.com/metosin/reitit/tree/master/examples/frontend-controllers).

## Nested controllers

When you nest routes in the route tree, the controllers get concatenated when
route data is merged. Consider this route tree:

```cljs
["/" {:controllers [{:start (fn [_] (js/console.log "root start"))}]}
 ["/item/:id"
  {:controllers [{:params (fn [match] (get-in match [:path-params :id]))
                  :start  (fn [item-id] (js/console.log "item start" item-id))
                  :stop   (fn [item-id] (js/console.log "item stop" item-id))}]}]]

```

* When you navigate to any route at all, the root controller gets started.
* If you navigate to `/item/something`, the root controller gets started first
  and then the item controller gets started.
* If you then navigate from `/item/something` to `/item/something-else`, first
  the item controller gets stopped with parameter `something` and then it gets
  started with the parameter `something-else`. The root controller stays on the
  whole time since its parameters do not change.

## Tips

### Authentication

Controllers can be used to load resources from a server. If and when your
API requires authentication you will need to implement logic to prevent controllers
trying to do requests if user isn't authenticated yet.

#### Run controllers and check authentication

If you have both unauthenticated and authenticated resources, you can
run the controllers always and then check the authentication status
on controller code, or on the code called from controllers (e.g. re-frame event
handler).

#### Disable controllers until user is authenticated

If all your resources require authentication an easy way to prevent bad
requests is to enable controllers only after authentication is done.
To do this you can check authentication status and call `apply-controllers`
only after authentication is done (also remember to manually call `apply-controllers`
with current `match` when authentication is done). Or if no navigation is possible
before authentication is done, you can start the router only after
authentication is done.

## Alternatives

Similar solution could be used to describe required resources as data (maybe
even GraphQL query) per route, and then have code automatically load
missing resources.

## Controllers elsewhere

* [Controllers in Keechma](https://keechma.com/guides/controllers/)
