# Just Coercion With Ring

A Sample project showing how to use the reitit coercion with pure ring.

* `Middleware` are turned into normal ring middleware via `reitit.middleware/chain`
* Endpoint parameters are given to middleware as arguments
* Coerced parameters are available from `:parameters`

## Usage

```clj
> lein repl

(require '[example.server :as server])

;; the manually coerced version
(require '[example.naive :as naive])
(server/restart naive/app)

;; schema-coercion
(require '[example.schema :as schema])
(server/restart schema/app)

;; spec-coercion
(require '[example.spec :as spec])
(server/restart spec/app)

;; data-spec-coercion
(require '[example.dspec :as dspec])
(server/restart dspec/app)
```

To test the endpoint:

http://localhost:3000/?x=1&y=20

## License

Copyright © 2017-2018 Metosin Oy
