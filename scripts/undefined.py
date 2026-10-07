#!/usr/bin/env python3
"""List names the generated Python reads but never defines (catches missing header helpers)."""
import ast, builtins, importlib, sys
src = open(sys.argv[1]).read()
tree = ast.parse(src)
defined = set(dir(builtins))
for n in ast.walk(tree):
    if isinstance(n, (ast.FunctionDef, ast.ClassDef)): defined.add(n.name)
    elif isinstance(n, ast.arg): defined.add(n.arg)
    elif isinstance(n, ast.ExceptHandler) and n.name: defined.add(n.name)
    elif isinstance(n, ast.ExceptHandler) and n.name: defined.add(n.name)
    elif isinstance(n, ast.Name) and isinstance(n.ctx, ast.Store): defined.add(n.id)
    elif isinstance(n, ast.Import): defined.update((a.asname or a.name).split('.')[0] for a in n.names)
    elif isinstance(n, ast.ImportFrom):
        for a in n.names:
            if a.name == '*':
                try: defined.update(dir(importlib.import_module(n.module)))
                except Exception as e: print(f"warning: can't import {n.module} for '*': {e}")
            else: defined.add(a.asname or a.name)
used = {n.id for n in ast.walk(tree) if isinstance(n, ast.Name) and isinstance(n.ctx, ast.Load)}
missing = sorted(used - defined)
print("undefined:", " ".join(missing) if missing else "(none)")
