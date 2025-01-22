#!/bin/bash
set -e
case $1 in
    cljs)
        npx shadow-cljs compile node-test
        node target/shadow-node-test/node-tests.js

        npx shadow-cljs compile karma
        npx karma start --single-run
        ;;
    clj)
        lein test-clj
        ;;
    *)
        echo "Please select [clj|cljs]"
        exit 1
        ;;
esac
