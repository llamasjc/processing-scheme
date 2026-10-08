#!/usr/bin/env python3
"""Run the generated Python for a test and evaluate its ';; py: <expr>' lines; each must be True."""
import contextlib, io, re, sys
scm, py = sys.argv[1], sys.argv[2]
checks = re.findall(r'^;; py: (.+)$', open(scm).read(), re.M)
if not checks: sys.exit(0)
src = "\n".join(l for l in open(py).read().splitlines() if l.strip() != "run()")
g, out, bad = {"__name__": "scmtest"}, io.StringIO(), 0
with contextlib.redirect_stdout(out):
    exec(compile(src, py, "exec"), g)
for c in checks:
    try:
        with contextlib.redirect_stdout(out): r = eval(c, g)
    except Exception as e: r = f"{type(e).__name__}: {e}"
    if r is not True: print(f"  check failed: {c}  ->  {r}"); bad = 1
sys.exit(bad)
