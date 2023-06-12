#!/bin/bash

set -e

for i in modules/*; do
  cd $i
  clojure -J-Dclojure.main.report=stderr -Tcljdoc-analyzer analyze-local
  cd ../..
done
