#!/bin/bash
# Checks every link from source to running JVM and prints where it breaks.
cd "$(dirname "$0")/.."
SRC=src/java/processing/mode/scheme
JAR=mode/SchemeMode.jar
LOG=/tmp/scheme-classload.log

echo "== 1. source"
grep -q "class _Pair" $SRC/Transpiler.java && echo "ok   Transpiler has _Pair" || echo "FAIL Transpiler has no _Pair (patch not applied)"
grep -q "setOnUpdate" $SRC/TsSchemeTokenMarker.java && echo "ok   marker instrumented" || echo "FAIL marker not instrumented"

echo "== 2. built jar"
unzip -tq $JAR >/dev/null 2>&1 && echo "ok   $JAR intact" || echo "FAIL $JAR corrupt"
unzip -p $JAR processing/mode/scheme/Transpiler.class | strings | grep -q _Pair \
  && echo "ok   jar Transpiler has _Pair" || echo "FAIL jar Transpiler is stale (build/install did not run)"

echo "== 3. other copies of SchemeMode classes Processing could load"
for d in /usr/lib/processing /usr/share/processing ~/Projects/processing4 ~/sketchbook; do
  [ -d "$d" ] || continue
  find -L "$d" -name '*.jar' 2>/dev/null | while read -r j; do
    [ "$(readlink -f "$j")" = "$(readlink -f $JAR)" ] && continue
    unzip -l "$j" 2>/dev/null | grep -q 'processing/mode/scheme/' && echo "WARN duplicate classes in $j"
  done
done

echo "== 4. what the JVM actually loads"
pkill -f '/bin/Processing|processing\.app\.'; while pgrep -f '/bin/Processing|processing\.app\.' >/dev/null; do sleep 0.5; done; sleep 2
rm -f /tmp/scheme-cl-*.log
JAVA_TOOL_OPTIONS="-Xlog:class+load=info:file=/tmp/scheme-cl-%p.log" /usr/lib/processing/bin/Processing >/tmp/scheme-run.log 2>&1 &
PID=$!
echo "Processing is starting. In the Processing window: open the sample sketch, press Run,"
echo "close the sketch window, type a few characters in the editor, then QUIT Processing."
echo "This script continues when Processing exits."
until pgrep -f 'processing\.app\.' >/dev/null; do sleep 0.5; done
while pgrep -f 'processing\.app\.' >/dev/null; do sleep 1; done
LOG=$(ls -S /tmp/scheme-cl-*.log 2>/dev/null | head -1)
echo "class log: $LOG ($(wc -l < "$LOG") lines)"
for c in SchemeMode TsSchemeTokenMarker Transpiler; do
  line=$(grep -m1 "processing\.mode\.scheme\.$c " $LOG)
  [ -z "$line" ] && { echo "?    $c never loaded"; continue; }
  srcpath=$(sed -n 's/.*source: //p' <<< "$line")
  case "$srcpath" in
    *"$(readlink -f $JAR)"*|*"$PWD/$JAR"*) echo "ok   $c from $JAR" ;;
    *) echo "FAIL $c loaded from: $srcpath" ;;
  esac
done
echo "== 5. highlight log"
grep "\[SchemeMode\]" /tmp/scheme-run.log | sort -u || true
grep -q "\[SchemeMode\]" /tmp/scheme-run.log || echo "(no [SchemeMode] lines)"
