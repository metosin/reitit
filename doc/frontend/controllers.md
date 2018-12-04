# Controllers (WIP)

* https://github.com/metosin/reitit/tree/master/examples/frontend-controllers

Controllers run code when a route is entered and left. This can be useful to:

- Load resources
- Update application state

Controller is map of:

- All properties are optional
- `:params` function, `match` -> controller parameters
- `:start` & `:stop` functions

Controllers are defined in [route data](../basics/route_data.md) and
as vectors, like:

```
["/items"
 {:name :items
  :controllers [{:start start-fn, :stop stop-fn)}]}]
```

To built hierarchies of controllers, nested route data can be used,
due to how Reitit merges the route data for nested routes the vectors are
concatenated:

```
["/items"
 {:controllers [{:start start-common-fn}]} ;; A
 [""
  {:name :items
   :controllers [{:start start-items-fn}]}] ;; B
 ["/:id"
  {:name :item
  ;; C
   :controllers [{:params item-param-fn :start start-item-fn}]}]]
```

In this example both `:items` and `:item` routes inherit `:controllers` from
their parent route. Lets call these controllers A, B and C. `:items` route
has controllers A and B and `:item` A and C.

Controller `:start` is called when new matches controllers list contains a
controller that is not yet initialized. If either of routes is entered
from some other route, `:start` is called for both of routes controllers.
If `:item` is entered from `:items` only C `:start` is called, because
A controller is identical for both routes. And transition from `:item`
back to `:items` would only call B `:start`.

Controller `:stop` function is called for controllers that were
initialized but aren't included in the new match.

To reinitialize a controller when route itself doesn't change,
controllers can provide `:params` function to control their
identity based on which parameters they are interested in.

To make C controller depend on routes `id` path parameter, it should
provide following function:

```clj
(defn item-param-fn [match]
  (select-keys (:path (:parameters match)) [:id]))
```

Now, whenever the result value of this function changes, the identity
of controller changes and controller is stopped and started.

TODO: Provide way to declare params as data.

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
