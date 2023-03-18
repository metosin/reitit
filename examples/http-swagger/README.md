# Http with Swagger/OpenAPI example

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
http POST :3000/math/plus x:=1 y:=2

http GET :3000/async results==1 seed==reitit
```

<img src="https://raw.githubusercontent.com/metosin/reitit/master/examples/http-swagger/swagger.png" />

## License

Copyright © 2018-2023 Metosin Oy
