#!/bin/bash
# Builds libjava-tree-sitter.so with only the Scheme grammar and swaps it into
# mode/java-tree-sitter-<ver>.jar. The version comes from the jar filename, so
# the native lib always matches the Java classes.
set -euo pipefail

MODE=$(cd "$(dirname "$0")/.." && pwd)
JAR=$(ls "$MODE"/mode/java-tree-sitter-*.jar | head -1)
VER=$(basename "$JAR" .jar); VER=${VER#java-tree-sitter-}
WORK="$MODE/native/.build"
JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(which javac)")")")}
mkdir -p "$WORK"

echo "== java-tree-sitter $VER"
[ -d "$WORK/src" ] || git -c advice.detachedHead=false clone -q --depth 1 \
  --branch "v$VER" https://github.com/seart-group/java-tree-sitter "$WORK/src"
cd "$WORK/src"
git submodule update -q --init --depth 1 tree-sitter tree-sitter-scheme

echo "== null-id guards in Language.cc"
LANG_CC=lib/ch_usi_si_seart_treesitter_Language.cc
grep -q ts_null_guard "$LANG_CC" || python3 - "$LANG_CC" << 'PY'
import re, sys
p = sys.argv[1]; s = open(p).read()
names = sorted(set(re.findall(r'\bts_language_(version|\w+_count)\s*\(', s)))
guards = "\n// ts_null_guard: grammars not compiled in have a null id\n"
for n in names:
    s = re.sub(rf'\bts_language_{n}\s*\(', f'ts_null_guard_{n}(', s)
    guards += f"static inline uint32_t ts_null_guard_{n}(const TSLanguage *l) {{ return l ? ts_language_{n}(l) : 0; }}\n"
end = [m.end() for m in re.finditer(r'^#include.*$', s, re.M)][-1]
open(p, "w").write(s[:end] + guards + s[end:])
print("guarded:", ", ".join(names))
PY

echo "== compile"
OBJ="$WORK/obj"; rm -rf "$OBJ"; mkdir -p "$OBJ"
COMMON=(-O2 -fPIC -fvisibility=hidden -Itree-sitter/lib/include -Itree-sitter/lib/src)
CSRC=(tree-sitter/lib/src/lib.c tree-sitter-scheme/src/parser.c)
[ -f tree-sitter-scheme/src/scanner.c ] && CSRC+=(tree-sitter-scheme/src/scanner.c)
for f in "${CSRC[@]}"; do
  gcc "${COMMON[@]}" -c "$f" -o "$OBJ/$(echo "$f" | tr / _).o"
done
for f in lib/*.cc; do
  g++ -std=c++17 "${COMMON[@]}" -Ilib -Iinclude \
      -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
      -DTS_LANGUAGE_SCHEME=1 -c "$f" -o "$OBJ/$(basename "$f").o"
done
SO="$WORK/libjava-tree-sitter.so"
g++ -shared -Wl,-z,defs -o "$SO" "$OBJ"/*.o

echo "== checks"
# capture first: grep -q exits early, nm gets SIGPIPE, pipefail fails the check
SYMS=$(nm -D --defined-only "$SO")
grep -q " JNI_OnLoad$" <<<"$SYMS" || { echo "JNI_OnLoad missing"; exit 1; }
grep -q Java_ch_usi_si_seart_treesitter_Language_scheme <<<"$SYMS" \
  || { echo "Language_scheme missing"; exit 1; }
if grep -q ' T ts_' <<<"$SYMS"; then echo "ts_* symbols leaked"; exit 1; fi

echo "== jar"
[ -f "$WORK/pristine.jar" ] || curl -sfL -o "$WORK/pristine.jar" \
  "https://repo1.maven.org/maven2/ch/usi/si/seart/java-tree-sitter/$VER/java-tree-sitter-$VER.jar"
cp "$WORK/pristine.jar" "$WORK/out.jar"
(cd "$WORK" && jar uf out.jar libjava-tree-sitter.so)
cp "$WORK/out.jar" "$JAR.tmp" && mv -f "$JAR.tmp" "$JAR"

if [ -f tree-sitter-scheme/queries/highlights.scm ]; then
  mkdir -p "$MODE/resources/queries"
  cp tree-sitter-scheme/queries/highlights.scm "$MODE/resources/queries/"
fi

echo "done: $(du -h "$SO" | cut -f1) lib in $(basename "$JAR")"
