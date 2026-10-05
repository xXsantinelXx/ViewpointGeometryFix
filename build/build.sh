#!/usr/bin/env bash
# Builds vpgeometryfix.jar, stages the mod folder and installs it for testing.
#
# Needs: a JDK (17+), projectzomboid.jar and ZombieBuddy.jar. Both JARs are
# only read, as compile classpath. Nothing in the game directory is written.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${BUILD_DIR:-$ROOT/build/out}"
CLASSES="$BUILD_DIR/classes"
VERSION_DIR="42.21"
JAR_NAME="vpgeometryfix.jar"

if [ -f "$ROOT/build/build.local" ]; then
    # shellcheck disable=SC1091
    source "$ROOT/build/build.local"
fi

if [ -z "${PZ_DIR:-}" ] || [ ! -f "$PZ_DIR/projectzomboid.jar" ]; then
    echo "[build] ERROR: Project Zomboid not found." >&2
    echo "        cp build/build.local.example build/build.local and set PZ_DIR," >&2
    echo "        or run: PZ_DIR=/path/to/ProjectZomboid build/build.sh" >&2
    exit 1
fi

ZB_JAR="${ZB_JAR:-$PZ_DIR/ZombieBuddy.jar}"
if [ ! -f "$ZB_JAR" ]; then
    echo "[build] ERROR: ZombieBuddy.jar not found at $ZB_JAR" >&2
    echo "        Install ZombieBuddy first (Workshop item 3619862853) or set ZB_JAR." >&2
    exit 1
fi

JAVAC="javac"
JAR="jar"
if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
    [ -x "$JAVAC" ] || { JAVAC="$JAVAC.exe"; JAR="$JAR.exe"; }
fi

# A running game holds a Windows file lock on the installed JAR, which would
# leave the mod folder half-replaced further down.
if [ -z "${SKIP_PZ_CHECK:-}" ] && command -v tasklist >/dev/null 2>&1; then
    if tasklist //FI "IMAGENAME eq ProjectZomboid64.exe" //FO CSV //NH 2>/dev/null \
            | grep -qi ProjectZomboid64; then
        echo "[build] ERROR: Project Zomboid is running. Close it first." >&2
        exit 1
    fi
fi

: "${MOD_INSTALL_ROOT:=${USERPROFILE:-$HOME}/Zomboid/mods/VPGeometryFix}"

# The classpath separator differs: ';' under Windows shells, ':' elsewhere.
case "$(uname -s 2>/dev/null || echo unknown)" in
    MINGW*|MSYS*|CYGWIN*) CP_SEP=';' ;;
    *) CP_SEP=':' ;;
esac

echo "[build] compiling"
rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES"
find "$ROOT/src" -name '*.java' > "$BUILD_DIR/sources.txt"
"$JAVAC" --release 17 -Xlint:all \
    -classpath "$PZ_DIR/projectzomboid.jar${CP_SEP}$ZB_JAR" \
    -d "$CLASSES" @"$BUILD_DIR/sources.txt"

echo "[build] packaging $JAR_NAME"
mkdir -p "$CLASSES/META-INF"
cp "$ROOT/LICENSE" "$CLASSES/META-INF/LICENSE"
"$JAR" --create --file "$BUILD_DIR/$JAR_NAME" -C "$CLASSES" .

echo "[build] staging the mod folder"
STAGE="$BUILD_DIR/stage/VPGeometryFix"
mkdir -p "$STAGE/$VERSION_DIR/media/java/client"
cp "$ROOT/resources/mod/mod.info" "$STAGE/mod.info"
cp "$ROOT/resources/mod/$VERSION_DIR/mod.info" "$STAGE/$VERSION_DIR/mod.info"
cp -r "$ROOT/resources/mod/$VERSION_DIR/media/lua" "$STAGE/$VERSION_DIR/media/lua"
cp "$BUILD_DIR/$JAR_NAME" "$STAGE/$VERSION_DIR/media/java/client/$JAR_NAME"

echo "[build] installing to $MOD_INSTALL_ROOT"
rm -rf "$MOD_INSTALL_ROOT"
mkdir -p "$(dirname "$MOD_INSTALL_ROOT")"
cp -r "$STAGE" "$MOD_INSTALL_ROOT"

echo "[build] done"
echo "        jar:     $BUILD_DIR/$JAR_NAME"
echo "        install: $MOD_INSTALL_ROOT"
