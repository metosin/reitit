# Ring example

A Sample project with ring.

## Usage

```clj
> lein repl
(start)
```

To test the endpoints using [httpie](https://httpie.org/):

```bash
# Plain
http GET :3000/plain/plus x==1 y==20
http POST :3000/plain/plus x:=1 y:=20

# Schema
http GET :3000/schema/plus x==1 y==20
http POST :3000/schema/plus x:=1 y:=20

# Data-specs
http GET :3000/dspec/plus x==1 y==20
http POST :3000/dspec/plus x:=1 y:=20

# Specs
http GET :3000/spec/plus x==1 y==20
http POST :3000/spec/plus x:=1 y:=20
```

## License

Copyright © 2017-2018 Metosin Oy
