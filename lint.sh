#!/bin/bash

EXIT=0

clj-kondo --lint modules/*/src test perf-test
EXIT=$(( EXIT + $? ))

for file in examples/*/src; do
    echo
    echo "Linting $file"
    clj-kondo --lint "$file"
    EXIT=$(( EXIT + $? ))
done

exit $EXIT
