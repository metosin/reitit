#!/bin/bash

set -e

# Need pom and jar for analyze local.
# Need repo version installed to the local m2 for up-to-date dependencies between modules.
# Install will run jar and pom tasks already.
./scripts/lein-modules install

# Number of modules analyzed at once, defaults to CPU count.
JOBS=${CLJDOC_CHECK_JOBS:-$(nproc)}
LOG_DIR=$(mktemp -d)
trap 'rm -rf "$LOG_DIR"' EXIT

analyze() {
  local module=$1
  local log="$LOG_DIR/$(basename "$module").log"
  if (cd "$module" && clojure -J-Dclojure.main.report=stderr -Tcljdoc-analyzer analyze-local) > "$log" 2>&1; then
    echo "::group::OK $module"
    cat "$log"
    echo "::endgroup::"
  else
    echo "::error::cljdoc analysis failed for $module"
    cat "$log"
    return 1
  fi
}
export -f analyze
export LOG_DIR

modules=()
for i in modules/*; do
  if [ "$(ls -A "$i/src")" ]; then
    modules+=("$i")
  else
    echo "Skip $i, empty src folder"
  fi
done

# Analyze the first module alone so the analyzer's own dependencies are
# downloaded once, instead of by several processes at the same time.
analyze "${modules[0]}"

printf '%s\n' "${modules[@]:1}" | xargs -P "$JOBS" -I{} bash -c 'analyze {}'
