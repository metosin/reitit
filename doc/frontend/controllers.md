# Controllers

* https://github.com/metosin/reitit/tree/master/examples/frontend-controllers

Controllers run code when a route is entered and left. This can be useful to:

- Load resources
- Update application state

## Authentication

Controllers can be used to load resources from a server. If and when your
API requires authentication you will need to implement logic to prevent controllers
trying to do requests if user isn't authenticated yet.

### Run controllers and check authentication

If you have both unauthenticated and authenticated resources, you can
run the controllers always and then check the authentication status
on controller code, or on the code called from controllers (e.g. re-frame event
handler).

### Disable controllers until user is authenticated

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
