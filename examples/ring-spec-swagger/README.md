# reitit-ring, clojure.spec, swagger, openapi 3

## Usage

```clj
> lein repl
(start)
```

- Swagger spec served at <http://localhost:3000/swagger.json>
- Openapi spec served at <http://localhost:3000/openapi.json>
- Swagger UI served at <http://localhost:3000/>

To test the endpoints using [httpie](https://httpie.org/):

```bash
http GET :3000/math/plus x==1 y==20
http POST :3000/math/plus x:=1 y:=20

http GET :3000/swagger.json
```

<img src="https://raw.githubusercontent.com/metosin/reitit/master/examples/ring-spec-swagger/swagger.png" />

## License

Copyright © 2017-2018 Metosin Oy
