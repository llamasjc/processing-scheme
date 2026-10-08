#!/bin/bash
# Golden-file tests for the transpiler.
#   scripts/regress.sh           diff every tests/*.scm against its .expected
#   scripts/regress.sh --update  rewrite the .expected files from current output
cd "$(dirname "$0")/.."
CP="$PWD/mode/SchemeMode.jar:/usr/share/processing/lib/app/*"
TXD="$HOME/.cache/scmtx"
if [ ! -f "$TXD/Tx.class" ]; then
  mkdir -p "$TXD"
  cat > "$TXD/Tx.java" << 'JAVA'
import processing.mode.scheme.*;
public class Tx {
    public static void main(String[] a) throws Exception {
        System.out.println(new Transpiler().transpile(Parser.parse(new String(System.in.readAllBytes())), "t"));
    }
}
JAVA
  javac -cp "$CP" -d "$TXD" "$TXD/Tx.java" || exit 1
fi
fail=0
for t in tests/*.scm; do
  full=$(java -cp "$TXD:$CP" Tx < "$t" 2>&1)
  printf '%s\n' "$full" > /tmp/regress_full.py
  out=$(printf '%s\n' "$full" | grep -v '# scm' | sed '1,/^# -- sketch --$/d')
  exp="${t%.scm}.expected"
  if [ "$1" = "--update" ]; then
    printf '%s\n' "$out" > "$exp"; echo "updated $exp"
  elif ! diff -u "$exp" <(printf '%s\n' "$out") > /tmp/regress.diff; then
    echo "FAIL $t"; cat /tmp/regress.diff; fail=1
  else
    echo "ok   $t"
  fi
  [ "$1" = "--update" ] && continue
  u=$(python3 scripts/undefined.py /tmp/regress_full.py)
  [ "$u" = "undefined: (none)" ] || { echo "  $u"; fail=1; }
  python3 scripts/runcheck.py "$t" /tmp/regress_full.py || { echo "FAIL $t (values)"; fail=1; }
done
exit $fail
