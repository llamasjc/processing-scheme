#!/usr/bin/env python3
"""Second pass after gen-keywords.py: add every name a SchemeMode sketch can call that
CppMode's keyword list doesn't cover (the Scheme runtime, the header's additions, the
installed mewnala), so the editor highlights all of them."""
import os, re, subprocess, sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
kw_file = root / "keywords.txt"
existing = {}
for line in kw_file.read_text().splitlines():
    if "\t" in line:
        w, t = line.split("\t", 1)
        existing[w] = t

hdr = subprocess.run(["java", "-cp", os.environ["TX"], "Tx"], input="(define x 1)",
                     capture_output=True, text=True).stdout
hdr = re.sub(r"^(run\(\)|_scm_wrap\(globals\(\)\))\s*$", "", hdr, flags=re.M)
g = {"__name__": "keywords"}
exec(hdr, g)

FORMS = ("define lambda let let* letrec letrec* if cond case when unless begin do set! quote quasiquote "
         "unquote unquote-splicing and or else define-syntax syntax-rules let-syntax letrec-syntax "
         "define-record-type define-values let-values let*-values guard delay delay-force make-promise "
         "parameterize case-lambda import include").split()

# Scheme names the transpiler maps onto a Python name without their ! or ?, so they can't be derived.
EXPLICIT = {
    "set_car": "set-car!", "set_cdr": "set-cdr!", "vector_fill": "vector-fill!",
    "hash_table_set": "hash-table-set!", "hash_table_delete": "hash-table-delete!",
    "hash_table_update": "hash-table-update!", "hash_table_update_default": "hash-table-update!/default",
    "hash_table_clear": "hash-table-clear!", "is_list": "list?", "is_pair": "pair?", "is_null": "null?",
}
CONVERT = ("string", "number", "symbol", "char", "integer", "list", "vector", "hash_table",
           "exact", "inexact", "utf8", "bytevector")
SKIP = {"run"}
SKIP_SCHEME = set("string-set vector-set id hash-code new-offscreen poll-for-sketch-update present readback-png update-raw sampler to-tuple background-image set-cursor-grab set-cursor-icon set-cursor-visible light-directional light-point light-spot draw-box draw-capsule draw-cone draw-conical-frustum draw-cylinder draw-plane draw-sphere draw-tetrahedron draw-torus".split())

def scheme_name(n):
    if n in EXPLICIT: return EXPLICIT[n]
    if n in SKIP: return None
    if re.search(r"(^|_)(lt|gt|eq)(_|$)", n): return None   # char<=? and friends: ambiguous
    s = n
    if s.endswith("_p"): s = s[:-2] + "?"
    for t in CONVERT:
        if s.startswith(t + "_to_"):
            s = t + "->" + s[len(t) + 4:]
            break
    return s.replace("_", "-")

added = []
for n, v in sorted(g.items()):
    if n.startswith("_") or n in ("keywords",): continue
    if isinstance(v, type(sys)): continue                      # modules
    if n[:1].isupper() and not n.isupper(): continue          # class names (Vec2, Graphics)
    name = n if n.isupper() else scheme_name(n)
    if not name or name in existing or name in SKIP_SCHEME: continue
    kind = "LITERAL2" if n.isupper() else ("FUNCTION2" if callable(v) else None)
    if kind is None: continue
    existing[name] = kind; added.append(name)
# R7RS procedures the transpiler compiles inline, so the header has no function for them.
BUILTINS = ("vector-set! string-set! display newline write error apply map for-each length list append "
            "reverse list-ref list-tail list-copy assoc assq assv member memq memv eq? eqv? equal? not "
            "null? boolean? quotient remainder modulo expt exact inexact floor ceiling round truncate "
            "sqrt exp log sin cos tan asin acos atan abs min max gcd lcm square number? integer? "
            "rational? real? zero? values call/cc raise string vector make-string string-upcase "
            "string-downcase string=? string<? string>? char=? char<? char>? symbol->string newline "
            "exit last-pair iota filter delete").split()
for f in BUILTINS:
    if f not in existing:
        existing[f] = "FUNCTION2"; added.append(f)
for f in FORMS:
    if f not in existing:
        existing[f] = "KEYWORD1"; added.append(f)

kw_file.write_text("".join(f"{w}\t{t}\n" for w, t in sorted(existing.items())))
print(f"added {len(added)} -> {len(existing)} keywords")
print(" ".join(added))
