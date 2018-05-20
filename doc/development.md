# Development Instructions

## Building

```bash
./scripts/lein-modules do clean, install
```

## Running tests

```bash
./scripts/test.sh clj
./scripts/test.sh cljs
```

## Documentation

The documentation is built with [gitbook](https://toolchain.gitbook.com). To preview your changes locally:

```bash
npm install -g gitbook-cli
gitbook install
gitbook serve
```

## To bump up version:

```bash
# new version
./scripts/set-version "1.0.0"
./scripts/lein-modules install

# works
lein test

# deploy to clojars
./scripts/lein-modules do clean, deploy clojars
```
