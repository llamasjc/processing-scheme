#!/usr/bin/env python3
"""SchemeMode keywords.txt, colored exactly like CppMode:
- every Processing/C++ word keeps CppMode's type (plus a kebab-case spelling)
- each Scheme word takes the type of its C++ counterpart in ANALOG
- Scheme words without a counterpart stay plain, like unknown identifiers in C++ mode"""
import re
from pathlib import Path
modes = Path(__file__).resolve().parents[2]
cpp = {}
for line in (modes / "CppMode/keywords.txt").read_text().splitlines():
    parts = line.split()
    if len(parts) >= 2 and not parts[0].startswith("#"):
        cpp.setdefault(parts[0], parts[1])

# Scheme word -> the C++/Processing word it corresponds to. Edit this to change a mapping.
ANALOG = {
    "if": "if", "cond": "switch", "case": "case", "else": "else",
    "when": "if", "unless": "if", "do": "do",
    "define": "void", "define-record-type": "class", "define-syntax": "void",
    "lambda": "auto", "let": "auto", "let*": "auto", "letrec": "auto", "letrec*": "auto",
    "and": "and", "or": "or", "not": "not",
    "guard": "try", "error": "throw", "import": "import",
    "display": "print", "newline": "println",
    "length": "length", "vector": "Array",
}

out = {}
for w, t in cpp.items():
    out[w] = t
    kebab = re.sub(r'(?<=[a-z0-9])([A-Z])', lambda m: '-' + m.group(1).lower(), w)
    if kebab != w and w[0].islower():
        out.setdefault(kebab, t)

mapped, plain = [], []
for scm, c in ANALOG.items():
    if c in cpp:
        out[scm] = cpp[c]; mapped.append(f"{scm}={c}:{cpp[c]}")
    else:
        out.pop(scm, None); plain.append(f"{scm} (no '{c}' in CppMode)")

dst = modes / "SchemeMode/keywords.txt"
if dst.exists(): dst.rename(dst.with_suffix(".txt.bak"))
dst.write_text("".join(f"{w}\t{t}\n" for w, t in sorted(out.items())))
print(f"{len(out)} keywords -> {dst}")
print("mapped:", "  ".join(mapped))
print("plain: ", "  ".join(plain) or "none")
