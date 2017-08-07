#!/bin/bash

set -euo pipefail

case $1 in
    cljs)
        echo "No support for ClojureScript coverage."
        ;;
    clj)
        COVERALLS_URL="https://coveralls.io/api/v1/jobs"
        lein cloverage --coveralls
        curl -F "json_file=@target/coverage/coveralls.json" "$COVERALLS_URL"
        ;;
    *)
        echo "Please select [clj|cljs]"
        exit 1
        ;;
esac

