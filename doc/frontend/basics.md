# Frontend basics

Reitit frontend integration is built from multiple layers:

- Core functions with some additional browser oriented features
- [Browser integration](./browser.md) for attaching Reitit to hash-change or HTML
history events
- Stateful wrapper for easy use of history integration
- Optional [controller extension](./controllers.md)

## Core functions

`reitit.frontend` provides few useful functions wrapping core functions:

`match-by-path` version which parses a URI using JavaScript, including
query-string, and also [coerces the parameters](../coercion/coercion.md).
Coerced parameters are stored in match `:parameters` property. If coercion
is not enabled, the original parameters are stored in the same property,
to allow the same code to read parameters regardless if coercion is
enabled.

`router` which compiles coercers by default.

`match-by-name` and `match-by-name!` with optional `path-paramers` and
logging errors to `console.warn` instead of throwing errors to prevent
React breaking due to errors.

## Next

[Browser integration](./browser.md)
