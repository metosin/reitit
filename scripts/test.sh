#!/bin/bash
set -e
case $1 in
    cljs)
        lein "do" test-browser once, test-node once, test-advanced once
        ;;
    clj)
        lein test-clj
        ;;
    *)
        echo "Please select [clj|cljs]"
        exit 1
        ;;
esac
