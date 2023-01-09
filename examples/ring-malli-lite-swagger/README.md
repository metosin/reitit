# reitit-ring, malli, swagger

## Usage

```clj
> lein repl
(start)
```

To test the endpoints using [httpie](https://httpie.org/):

```bash
http GET :3000/math/plus x==1 y==20
http POST :3000/math/plus x:=1 y:=20

http GET :3000/swagger.json
```

<img src="https://raw.githubusercontent.com/metosin/reitit/master/examples/ring-spec-swagger/swagger.png" />

## License

Copyright © 2017-2019 Metosin Oy
