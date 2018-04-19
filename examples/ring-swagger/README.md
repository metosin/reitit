# WIP: Ring + Swagger example

TODO:

* Serve Swagger-ui.

## Usage

```clj
> lein repl

(require '[example.server :as server])

(server/start)
```

To test the endpoints using [httpie](https://httpie.org/):

```bash
http GET :3000/api/schema/plus x==1 y==20
http GET :3000/api/spec/plus x==1 y==20

http GET :3000/api/swagger.json
```

## License

Copyright © 2017 Metosin Oy
