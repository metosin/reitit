#!/bin/bash

set -e

# Need pom and jar for analyze local.
# Need repo version installed to the local m2 for up-to-date dependencies between modules.
# Install will run jar and pom tasks already.
./scripts/lein-modules install

for i in modules/*; do
  cd $i
  if [ "$(ls -A src)" ]; then
    clojure -J-Dclojure.main.report=stderr -Tcljdoc-analyzer analyze-local
  else
    echo "Skip $i, empty src folder"
  fi
  cd ../..
done
