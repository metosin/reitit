# Frontend basics

Reitit frontend integration is built from multiple layers:

- Core functions with some additional browser oriented features
- [Browser integration](./browser.md) for attaching Reitit to hash-change or HTML
history events
- Stateful wrapper for easy use of history integration
- Optional [controller extension](./controllers.md)

You likely won't use `reitit.frontend` directly in your apps and instead you
will use the API documented in the browser integration docs, which wraps these
lower level functions.

## Core functions

`reitit.frontend` provides some useful functions wrapping core functions:

`match-by-path` version which parses a URI using JavaScript, including
query-string, and also [coerces the parameters](../coercion/coercion.md).
Coerced parameters are stored in match `:parameters` property. If coercion
is not enabled, the original parameters are stored in the same property,
to allow the same code to read parameters regardless if coercion is
enabled.

`router` which compiles coercers by default.

`match-by-name` and `match-by-name!` with optional `path-paramers` and
logging errors to `console.warn` instead of throwing errors to prevent
React breaking due to errors. These can also [encode query-parameters](./coercion.md)
using schema from match data.

## Next

[Browser integration](./browser.md)
