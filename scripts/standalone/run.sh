#!/bin/sh
# Runs a Scheme sketch without the Processing IDE: ./run.sh sketch.scm
HERE=$(cd "$(dirname "$0")" && pwd -P)
SKETCH=${1:-"$HERE/examples/Basics/HelloCircle/HelloCircle.scm"}
case "$(uname -s)-$(uname -m)" in
  Linux-x86_64) P=linux-x86_64 ;;
  Darwin-arm64) P=macos-aarch64 ;;
  *) echo "no Python for $(uname -s) $(uname -m) in this package"; exit 1 ;;
esac
PY="$HERE/python/$P/bin/python3"
[ -x "$PY" ] || chmod +x "$HERE/python/$P/bin/"* 2>/dev/null
[ "$(uname -s)" = Darwin ] && xattr -dr com.apple.quarantine "$HERE/python/$P" 2>/dev/null
command -v java >/dev/null 2>&1 || { echo "Java 17 or newer is needed to turn Scheme into Python (https://adoptium.net)"; exit 1; }
DIR=$(cd "$(dirname "$SKETCH")" && pwd -P)
NAME=$(basename "$SKETCH" .scm)
mkdir -p "$DIR/build"
java -cp "$HERE/lib/SchemeMode.jar" processing.mode.scheme.SchemeCli "$SKETCH" "$DIR/build/$NAME.py" || exit 1
# files the sketch loads, as the IDE lays them out
if [ -d "$DIR/data" ]; then rm -rf "$DIR/build/assets"; cp -r "$DIR/data" "$DIR/build/assets"; fi
cd "$DIR/build" || exit 1
export RUST_LOG=error BEVY_LOG=error WGPU_LOG=error RUST_LOG_STYLE=never
[ "$(uname -s)" = Linux ] && export MEWNALA_BACKEND=glfw-x11 GDK_BACKEND=x11
exec "$PY" -u "$NAME.py"
