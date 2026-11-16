#!/bin/bash
set -e
case $1 in
    cljs)
        npx shadow-cljs compile node-test
        node target/shadow-node-test/node-tests.js

        rm -rf target/karma
        npx shadow-cljs compile karma
        npx karma start --single-run

        rm -rf target/karma
        npx shadow-cljs release karma
        npx karma start --single-run
        ;;
    clj11)
        lein test-clj11
        ;;
    clj12)
        lein test-clj
        ;;
    bb)
        bb test-bb
        ;;
    *)
        echo "Please select [clj11|clj12|cljs|bb]"
        exit 1
        ;;
esac
