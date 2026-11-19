#!/usr/bin/env python3
"""Builds java-tree-sitter's native library with only the Scheme grammar, for
the computer it runs on: native/<platform>/libjava-tree-sitter.so (Linux),
.dylib (macOS, Intel and Apple Silicon in one file) or java-tree-sitter.dll
(Windows, with MinGW). SchemeMode loads it from there itself, so syntax
highlighting works on Windows too (java-tree-sitter's own loader only knows
Linux and macOS).

    native/build_ts.py <java-tree-sitter version>
"""
import os, platform, re, shutil, subprocess, sys
from pathlib import Path

VER = sys.argv[1]
MODE = Path(__file__).resolve().parent.parent
WORK = MODE / "native" / ".build-ts"
SYS = platform.system()
ARM = platform.machine().lower() in ("arm64", "aarch64")
PLAT = {"Linux": "linux-aarch64" if ARM else "linux-x86_64",
        "Darwin": "macos", "Windows": "windows-x86_64"}[SYS]
LIB = {"Linux": "libjava-tree-sitter.so", "Darwin": "libjava-tree-sitter.dylib",
       "Windows": "java-tree-sitter.dll"}[SYS]
JAVA_HOME = Path(os.environ["JAVA_HOME"])


def run(*cmd, cwd=None):
    print(" ".join(map(str, cmd)), flush=True)
    subprocess.run([str(c) for c in cmd], cwd=cwd, check=True)


src = WORK / "src"
if not src.exists():
    WORK.mkdir(parents=True, exist_ok=True)
    run("git", "-c", "advice.detachedHead=false", "clone", "-q", "--depth", "1",
        "--branch", f"v{VER}", "https://github.com/seart-group/java-tree-sitter", src)
run("git", "submodule", "update", "-q", "--init", "--depth", "1", "tree-sitter", "tree-sitter-scheme", cwd=src)

# grammars that aren't compiled in have a null id: guard the calls on it
lang_cc = src / "lib" / "ch_usi_si_seart_treesitter_Language.cc"
s = lang_cc.read_text()
if "ts_null_guard" not in s:
    names = sorted(set(re.findall(r"\bts_language_(version|\w+_count)\s*\(", s)))
    guards = "\n// ts_null_guard: grammars not compiled in have a null id\n"
    for n in names:
        s = re.sub(rf"\bts_language_{n}\s*\(", f"ts_null_guard_{n}(", s)
        guards += (f"static inline uint32_t ts_null_guard_{n}(const TSLanguage *l) "
                   f"{{ return l ? ts_language_{n}(l) : 0; }}\n")
    end = [m.end() for m in re.finditer(r"^#include.*$", s, re.M)][-1]
    lang_cc.write_text(s[:end] + guards + s[end:])

jni_os = {"Linux": "linux", "Darwin": "darwin", "Windows": "win32"}[SYS]
inc = ["-I", src / "tree-sitter/lib/include", "-I", src / "tree-sitter/lib/src",
       "-I", src / "lib", "-I", src / "include",
       "-I", JAVA_HOME / "include", "-I", JAVA_HOME / "include" / jni_os]
arch = ["-arch", "x86_64", "-arch", "arm64", "-mmacosx-version-min=11.0"] if SYS == "Darwin" else []
pic = [] if SYS == "Windows" else ["-fPIC"]
cc, cxx = ("clang", "clang++") if SYS == "Darwin" else ("gcc", "g++")

obj = WORK / "obj"
shutil.rmtree(obj, ignore_errors=True)
obj.mkdir()
csrc = [src / "tree-sitter/lib/src/lib.c", src / "tree-sitter-scheme/src/parser.c"]
if (src / "tree-sitter-scheme/src/scanner.c").exists():
    csrc.append(src / "tree-sitter-scheme/src/scanner.c")
objs = []
for f in csrc:
    o = obj / (f.parent.parent.name + "_" + f.stem + ".o")
    run(cc, "-O2", *pic, *arch, "-fvisibility=hidden", *inc, "-c", f, "-o", o)
    objs.append(o)
for f in sorted((src / "lib").glob("*.cc")):
    o = obj / (f.stem + ".o")
    run(cxx, "-std=c++17", "-O2", *pic, *arch, "-fvisibility=hidden", *inc,
        "-DTS_LANGUAGE_SCHEME=1", "-c", f, "-o", o)
    objs.append(o)

out = MODE / "native" / PLAT
out.mkdir(parents=True, exist_ok=True)
lib = out / LIB
if SYS == "Darwin":
    run(cxx, "-dynamiclib", *arch, "-o", lib, *objs)
elif SYS == "Windows":
    # no MinGW runtime DLLs to ship: link them in
    # --export-all-symbols: the Java_* functions aren't all marked for export
    run(cxx, "-shared", "-static", "-static-libgcc", "-static-libstdc++",
        "-Wl,--export-all-symbols", "-o", lib, *objs)
else:
    run(cxx, "-shared", "-Wl,-z,defs", "-o", lib, *objs)
print(f"done: {lib} ({lib.stat().st_size // 1024} KB)")

hl = src / "tree-sitter-scheme/queries/highlights.scm"
if hl.exists():
    (MODE / "resources" / "queries").mkdir(parents=True, exist_ok=True)
    shutil.copy(hl, MODE / "resources" / "queries" / "highlights.scm")
