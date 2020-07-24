# Buddy auth example

A sample project that shows how to use [Buddy] authentication with Reitit to implement simple authentication and authorization flows.

* Basic auth
* Token-based authorization with JWT tokens

[Buddy]: https://github.com/funcool/buddy

## Usage

Start a REPL:

```sh
lein repl
```

Start the server:

```clj
(start)
```

Take a look at the annotated example in [server.clj](src/example/server.clj).

You can also try some curl commands:

```sh
# Let's first try without password - this should fail
curl http://localhost:3000/basic-auth

# With password, it should work
curl http://user1:kissa13@localhost:3000/basic-auth

# The response should look something like this:
#
#    {"message":"Basic auth succeeded!","user":{"id":1,"roles":["admin","user"],
#     "token":"eyJhbGciOiJIUzUxMiJ9.eyJpZCI6MSwicm9sZXMiOlsiYWRtaW4iLCJ1c2VyIl0sImV4cCI6MTU5NTU5NDcxNn0.lPFcLxWMFK4_dCLZs2crPB2rmvwO6f-uRsRYdhaWTAJHGKIQpP8anjbmnz6QlrS_RlI160FVzZohPlmkS9JfIQ"}}
#
# The value in the JSON field `token` is a JWT token. A new one is generated with every call and they expire in two hours.

# We can try token auth then. Copy the token from the response in the next command:
curl -H "Authorization: Token PASTE_YOUR_TOKEN_HERE" http://localhost:3000/token-auth

```

## License

Copyright © Metosin Oy and collaborators.
