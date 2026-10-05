#!/usr/bin/env bash
# Runs all offline tests. Requires: JDK 17+, python3 with lupa (pip install lupa).
# Build first (build/build.sh) - tests use build/out/classes.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/out"
[[ -d "$OUT/classes" ]] || { echo "run build/build.sh first" >&2; exit 1; }
rm -rf "$OUT/test-classes"; mkdir -p "$OUT/test-classes"
javac --release 17 -cp "$OUT/classes:$OUT/stubs" -d "$OUT/test-classes" $(find "$ROOT/tests/java" -name '*.java')
java -cp "$OUT/classes:$OUT/stubs:$OUT/test-classes" vpgeometryfix.tests.AllTests
python3 "$ROOT/tests/lua/test_lua.py"
