help:
    @just --list

# Initializes lint
init-lint:
    clj-kondo --lint $(lein classpath)

# Lints the project
lint:
    ./lint.sh
