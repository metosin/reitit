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

## Formatting

```bash
clojure-lsp format
clojure-lsp clean-ns
```

## Documentation

The documentation lives under `doc` and it is hosted on [cljdoc](https://cljdoc.org). See their
documentation for [library authors](https://github.com/cljdoc/cljdoc/blob/master/doc/userguide/for-library-authors.adoc)

## Updating deps


* `lein ancient upgrade`
* Mention non-dev non-test dep upgrades in CHANGELOG.md
* `npm update --save`
* Make a PR, run CI

## Making a release

We use [Break Versioning][breakver]. Remember our promise: patch-level bumps never include breaking changes!

[breakver]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md

```bash
# new version
./scripts/set-version "1.0.0"

# create a release commit and a tag
git add -u
git commit -m "Release 1.0.0"
git tag 1.0.0

# works
./scripts/lein-modules install
lein test

# deploy to clojars
CLOJARS_USERNAME=*** CLOJARS_PASSWORD=*** ./scripts/lein-modules do clean, deploy clojars

# push the commit and the tag
git push
git push --tags
```

* Remember to update the changelog!
* Announce the release at least on #reitit in Clojurians.
