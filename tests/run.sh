#!/usr/bin/env bash
# Offline checks: compiles the mod against the stubs in tests/stubs and runs
# the harness. No Project Zomboid, no ZombieBuddy, no game launch.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${BUILD_DIR:-$ROOT/build/test}"
rm -rf "$OUT"
mkdir -p "$OUT/classes"

echo "[test] compiling stubs, mod and harness"
find "$ROOT/tests/stubs" "$ROOT/src" "$ROOT/tests/pzmod" -name '*.java' > "$OUT/sources.txt"
javac --release 17 -Xlint:all -d "$OUT/classes" @"$OUT/sources.txt"

echo "[test] running the harness"
java -cp "$OUT/classes" pzmod.vpgeometryfix.ProbeTest

echo "[test] checking mod.info against the Java package"
pkg=$(grep -E '^javaPkgName=' "$ROOT/resources/mod/42.21/mod.info" | cut -d= -f2)
test -f "$ROOT/src/${pkg//.//}/Main.java" \
    || { echo "[test] javaPkgName=$pkg has no Main.java"; exit 1; }
jar=$(grep -E '^javaJarFile=' "$ROOT/resources/mod/42.21/mod.info" | cut -d= -f2)
case "$jar" in
    media/java/client/*.jar) ;;
    *) echo "[test] javaJarFile should be a client-only path: $jar"; exit 1 ;;
esac
grep -q '^require=\\ZombieBuddy' "$ROOT/resources/mod/42.21/mod.info" \
    || { echo "[test] the version mod.info must require \\ZombieBuddy"; exit 1; }

echo "[test] checking that this release patches nothing"
if grep -rn '@Patch' "$ROOT/src" >/dev/null; then
    echo "[test] found a @Patch annotation: the diagnosis release must not patch"
    exit 1
fi

echo "[test] checking the Lua entry points"
for symbol in 'Events.OnGameStart.Add' 'Events.OnKeyPressed.Add' 'VPGeometryFix_Options'; do
    grep -rq "$symbol" "$ROOT/resources/mod/42.21/media/lua/client" \
        || { echo "[test] missing in the Lua files: $symbol"; exit 1; }
done

echo "[test] OK"
