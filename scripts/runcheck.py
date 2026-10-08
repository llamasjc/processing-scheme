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
def _plain(x):
    if isinstance(x, str): return str.__str__(x)
    if isinstance(x, list): return [_plain(y) for y in x]
    if type(x).__name__ == "_Pair": return (_plain(x.car), _plain(x.cdr))
    return x
g["__plain"] = _plain
def _line_of(f):
    try: f()
    except Exception as e: return g["_scm_where"](e.__traceback__)
    return None
g["line_of"] = _line_of
def _err_of(f):
    try: f()
    except Exception as e: return str(e)
    return None
g["err_of"] = _err_of
import ast, builtins
class _Wrap(ast.NodeTransformer):
    # Results of sketch calls are compared as plain Python values (symbols -> str).
    # Write raw(f(...)) to keep a result as-is, e.g. to inspect symbol-ness.
    def visit_Call(self, n):
        if isinstance(n.func, ast.Name) and n.func.id == "raw":
            return n.args[0]
        self.generic_visit(n)
        f = n.func.id if isinstance(n.func, ast.Name) else None
        if f and f in g and not hasattr(builtins, f) and not f.startswith("_"):
            return ast.Call(ast.Name("__plain", ast.Load()), [n], [])
        return n
def _prep(c):
    tree = ast.fix_missing_locations(_Wrap().visit(ast.parse(c, mode="eval")))
    return compile(tree, "<check>", "eval")
for c in checks:
    try:
        with contextlib.redirect_stdout(out): r = eval(_prep(c), g)
    except Exception as e: r = f"{type(e).__name__}: {e}"
    if r is not True: print(f"  check failed: {c}  ->  {r}"); bad = 1
sys.exit(bad)
