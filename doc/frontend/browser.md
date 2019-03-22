# Frontend browser integration

Reitit includes two browser history integrations.

Functions follow HTML5 History API: `push-state` to change route, `replace-state`
to change route without leaving previous entry in browser history.

## Fragment router

Fragment is simple integration which stores the current route in URL fragment,
i.e. after `#`. This means the route is never part of the request URI and
server will always know which file to return (`index.html`).

## HTML5 router

HTML5 History API can be used to modify the URL in browser without making
request to the server. This means the URL will look normal, but the downside is
that the server must respond to all routes with correct file (`index.html`).
Check examples for simple Ring handler example.

## Easy

Reitit frontend routers require storing the state somewhere and passing it to
all the calls. Wrapper `reitit.frontend.easy` is provided which manages
a router instance and passes the instance to all calls. This should
allow easy use in most applications, as browser anyway can only have single
event handler for page change events.

## History manipulation

Reitit doesn't include functions to manipulate the history stack, i.e.
go back or forwards, but calling History API functions directly should work:

```
(.go js/window.history -1)
;; or
(.back js/window.history)
```
