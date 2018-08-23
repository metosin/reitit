# Frontend basics

* https://github.com/metosin/reitit/tree/master/examples/frontend

**WIP**

`reitit.frontend` provides few useful functions wrapping core functions:

- `match-by-path` version which parses a URI using JavaScript, including
query-string, and also coerces the parameters.
- `router` which compiles coercers by default
- `match-by-name` and `match-by-name!` with optional `path-paramers` and
logging errors to `console.warn` instead of throwing errors (to prevent
React breaking due to errors).
