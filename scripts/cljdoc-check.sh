#!/bin/bash

set -e

# Need pom and jar for analyze local.
# Need repo version installed to the local m2 for up-to-date dependencies between modules.
# Install will run jar and pom tasks already.
./scripts/lein-modules install

# Number of modules analyzed at once, defaults to CPU count.
JOBS=${CLJDOC_CHECK_JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)}
LOG_DIR=$(mktemp -d)
trap 'rm -rf "$LOG_DIR"' EXIT
export LOG_DIR

# Output goes to $LOG_DIR/<module>.log and a failure leaves
# $LOG_DIR/<module>.failed. Logs are printed at the end, so output from
# parallel runs doesn't interleave.
analyze() {
  local module=$1
  local name
  name=$(basename "$module")
  echo "Analyzing $module"
  rm -f "$LOG_DIR/$name.failed"
  if (cd "$module" && clojure -J-Dclojure.main.report=stderr -Tcljdoc-analyzer analyze-local) > "$LOG_DIR/$name.log" 2>&1; then
    echo "Done $module"
  else
    echo "Failed $module"
    touch "$LOG_DIR/$name.failed"
  fi
}
export -f analyze

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

printf '%s\0' "${modules[@]:1}" | xargs -0 -n 1 -P "$JOBS" bash -c 'analyze "$1"' _

# Parallel runs can still download the same dependencies at the same time,
# so retry failures once, one at a time.
for module in "${modules[@]}"; do
  if [ -e "$LOG_DIR/$(basename "$module").failed" ]; then
    echo "Retrying $module"
    analyze "$module"
  fi
done

fail=0
for module in "${modules[@]}"; do
  name=$(basename "$module")
  if [ -e "$LOG_DIR/$name.failed" ]; then
    echo "::error::cljdoc analysis failed for $module"
    cat "$LOG_DIR/$name.log"
    fail=1
  else
    echo "::group::OK $module"
    cat "$LOG_DIR/$name.log"
    echo "::endgroup::"
  fi
done

exit $fail
