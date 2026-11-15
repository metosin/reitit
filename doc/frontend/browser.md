# Frontend browser integration

Reitit includes two browser history integrations.

Main functions are `navigate` and `set-query`. Navigate is used to navigate
to named routes, and the options parameter can be used to control all
parameters and if `pushState` or `replaceState` should be used to control
browser history stack. The `set-query` function can be used to change
or modify query parameters for the current route, it takes either map of
new query params or function from old params to the new params.

There are also secondary functions following HTML5 History API:
`push-state` to navigate to new route adding entry to the history and
`replace-state` to change route without leaving previous entry in browser history.

See [coercion notes](./coercion.md) to see how frontend route parameters
can be decoded and encoded.

## Fragment router

Fragment is simple integration which stores the current route in URL fragment,
i.e. after `#`. This means the route is never part of the request URI and
server will always know which file to return (`index.html`).

## HTML5 router

HTML5 History API can be used to modify the URL in browser without making
request to the server. This means the URL will look normal, but the downside is
that the server must respond to all routes with correct file (`index.html`).
Check examples for simple Ring handler example.

### Anchor click handling

HTML5 History router will handle click events on anchors where the href
matches the route tree (and other [rules](../../modules/reitit-frontend/src/reitit/frontend/history.cljs#L84-L98)).
If you have need to control this logic, for example to handle some
anchor clicks where the href matches route tree normally (i.e. browser load)
you can provide `:ignore-anchor-click?` function to add your own logic to
event handling:

```clj
(rfe/start!
  router
  on-navigate-fn
  {:use-fragment false
   :ignore-anchor-click? (fn [router e el uri]
                           ;; Add additional check on top of the default checks
                           (and (rfh/ignore-anchor-click? router e el uri)
                                (not= "false" (gobj/get (.-dataset el) "reititHandleClick"))))})

;; Use data-reitit-handle-click to disable Reitit anchor handling
[:a
 {:href (rfe/href ::about)
  :data-reitit-handle-click false}
 "About"]
```

## Easy

Reitit frontend routers require storing the state somewhere and passing it to
all the calls. Wrapper `reitit.frontend.easy` is provided which manages
a router instance and passes the instance to all calls. This should
allow easy use in most applications, as browser anyway can only have single
event handler for page change events.

## History manipulation

Reitit doesn't include functions to manipulate the history stack, i.e.,
go back or forwards, but calling History API functions directly should work:

```
(.go js/window.history -1)
;; or
(.back js/window.history)
```
