#!/usr/bin/env bash
# Builds the installable mod folder and zip. Needs only a JDK (17+); no game
# JAR, no ZombieBuddy JAR and no network: all game access is reflective.
#   build/build.sh            -> build/out/mod/ViewpointGeometryFix + build/dist/*.zip
#   build/build.sh --test     -> additionally runs tests/run-tests.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION")"
OUT="$ROOT/build/out"
MOD="$OUT/mod/ViewpointGeometryFix"
DIST="$ROOT/build/dist"
# Fixed timestamp => byte-identical JAR for identical sources.
STAMP="2026-01-01T00:00:00Z"

rm -rf "$OUT"
mkdir -p "$OUT/stubs" "$OUT/classes" "$MOD" "$DIST"

echo "[build] compile stubs (compile-only, not packaged)"
javac --release 17 -nowarn -d "$OUT/stubs" $(find "$ROOT/src/stubs/java" -name '*.java')

echo "[build] compile mod sources"
javac --release 17 -Xlint:all -Werror -cp "$OUT/stubs" -d "$OUT/classes" $(find "$ROOT/src/main/java" -name '*.java')

if find "$OUT/classes" -path '*se/krka*' | grep -q .; then
  echo "[build] ERROR: stub classes leaked into the mod classes" >&2; exit 1
fi

echo "[build] assemble mod folder"
cp -R "$ROOT/resources/mod/." "$MOD/"
sed -i.bak "s/^modversion=.*/modversion=$VERSION/" "$MOD/42/mod.info" && rm -f "$MOD/42/mod.info.bak"
mkdir -p "$MOD/42/media/java/client"
cat > "$OUT/MANIFEST.MF" <<MF
Implementation-Title: ViewpointGeometryFix
Implementation-Version: $VERSION
MF
jar --create --date="$STAMP" --file "$MOD/42/media/java/client/ViewpointGeometryFix.jar" \
    --manifest "$OUT/MANIFEST.MF" -C "$OUT/classes" .
cp "$ROOT/LICENSE" "$MOD/LICENSE.txt"

echo "[build] VPGF Doctor (standalone checker, runs outside the game)"
mkdir -p "$OUT/doctor-classes" "$OUT/doctor/VPGF-Doctor"
javac --release 17 -Xlint:all -Werror -d "$OUT/doctor-classes" $(find "$ROOT/src/doctor/java" -name '*.java')
printf 'Main-Class: vpgeometryfix.doctor.Doctor\nImplementation-Version: %s\n' "$VERSION" > "$OUT/DOCTOR.MF"
jar --create --date="$STAMP" --file "$OUT/doctor/VPGF-Doctor/VPGF-Doctor.jar" --manifest "$OUT/DOCTOR.MF" -C "$OUT/doctor-classes" .
# Windows batch files need CRLF line endings
sed 's/\r*$/\r/' "$ROOT/resources/doctor/VPGF-Doctor.bat" > "$OUT/doctor/VPGF-Doctor/VPGF-Doctor.bat"

echo "[build] zip"
ZIP="$DIST/ViewpointGeometryFix-$VERSION.zip"
rm -f "$ZIP"
python3 - "$OUT/mod" "$ZIP" <<'PY'
import os, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as z:
    for base, dirs, files in os.walk(src):
        dirs.sort()
        for f in sorted(files):
            p = os.path.join(base, f)
            info = zipfile.ZipInfo(os.path.relpath(p, src).replace(os.sep, "/"), (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            with open(p, "rb") as fh:
                z.writestr(info, fh.read())
PY
DOCTOR_ZIP="$DIST/VPGF-Doctor-$VERSION.zip"
rm -f "$DIST"/VPGF-Doctor-*.zip
python3 - "$OUT/doctor" "$DOCTOR_ZIP" <<'PY'
import os, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as z:
    for base, dirs, files in os.walk(src):
        dirs.sort()
        for f in sorted(files):
            p = os.path.join(base, f)
            info = zipfile.ZipInfo(os.path.relpath(p, src).replace(os.sep, "/"), (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            with open(p, "rb") as fh:
                z.writestr(info, fh.read())
PY
sha256sum "$ZIP" "$DOCTOR_ZIP" "$MOD/42/media/java/client/ViewpointGeometryFix.jar"
echo "[build] done: $ZIP"

if [[ "${1:-}" == "--test" ]]; then
  "$ROOT/tests/run-tests.sh"
fi
