#!/usr/bin/env python3
"""Doc tests for SchemeMode: run every Scheme example in docs/**/*.md and check its annotations.

  (expr) ;=> value                     the expression gives this value (no window)
  ;; pixel X Y => R G B                this pixel has this color after 30 frames
  ;; region X Y W H has R G B          some pixel in the rectangle has this color
  ;; output: TEXT                      the sketch prints a line containing TEXT
  ;; file NAME contains TEXT           the sketch writes NAME, containing TEXT
  ;; frames N                          take the frame after N frames instead of 30
A block without annotations must still transpile.
Usage: scripts/doctest.py [-k PATTERN] [FILES...]
"""
import os, re, sys, glob, json, shutil, struct, subprocess, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.join(HERE, '..', 'docs')
ROOT = os.path.expanduser('~/sketchbook/.scm_doctest')
TX = os.environ.get('TX')
if not TX:
    sys.exit('run scm first (TX is not set)')
TOL = 12

def read_png(path):
    data = open(path, 'rb').read()
    pos, idat, w, h, bpp = 8, b'', 0, 0, 4
    while pos < len(data):
        n, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
            bpp = 4 if ctype == 6 else 3
        elif kind == b'IDAT':
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat); stride = w * bpp
    out = bytearray(h * stride); prev = bytearray(stride); i = 0
    for y in range(h):
        f = raw[i]; i += 1
        row = bytearray(raw[i:i + stride]); i += stride
        for x in range(stride):
            a = row[x - bpp] if x >= bpp else 0; b = prev[x]; c = prev[x - bpp] if x >= bpp else 0
            if f == 1: row[x] = (row[x] + a) & 255
            elif f == 2: row[x] = (row[x] + b) & 255
            elif f == 3: row[x] = (row[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                row[x] = (row[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out[y * stride:(y + 1) * stride] = row; prev = row
    return w, h, bpp, out

def px(img, x, y):
    w, h, bpp, d = img
    if not (0 <= x < w and 0 <= y < h): return None
    k = (y * w + x) * bpp
    return (d[k], d[k + 1], d[k + 2])

def close(c, want): return c is not None and all(abs(a - b) <= TOL for a, b in zip(c, want))

def transpile(src):
    r = subprocess.run(['java', '-cp', TX, 'Tx'], input=src, capture_output=True, text=True)
    if r.returncode or not r.stdout.strip():
        return None, (r.stderr.strip().splitlines() or ['transpile failed'])[0][:300]
    return r.stdout, None

def blocks(path):
    text = open(path).read()
    for m in re.finditer(r'```scheme\n(.*?)```', text, re.S):
        yield text.count('\n', 0, m.start()) + 2, m.group(1)

def run_values(code, workdir):
    defs, checks = [], []
    for line in code.splitlines():
        if ';=>' in line:
            expr, want = line.split(';=>', 1)
            checks.append((expr.strip(), want.strip()))
        else:
            defs.append(line)
    src = '\n'.join(defs) + '\n' + '\n'.join(
        '(define (dtcheck-%d) (let ((v %s)) (list (equal? v (quote %s)) v)))' % (i, e, w) for i, (e, w) in enumerate(checks))
    py, err = transpile(src)
    if err: return [(False, 'transpile: ' + err)]
    py = re.sub(r'^(run\(\)|_scm_wrap\(globals\(\)\))\s*$', '', py, flags=re.M)
    py += ('\nimport json as _dtj\n_dtr = []\nfor _dti in range(%d):\n'
           '    try:\n        _ok, _v = globals()["dtcheck_%%d" %% _dti]()\n        _dtr.append([bool(_ok), _show(_v)])\n'
           '    except Exception as _e:\n        _dtr.append([False, "error: %%s" %% _e])\n'
           'print("DTRESULT " + _dtj.dumps(_dtr), flush=True)\n' % len(checks))
    f = os.path.join(workdir, 'values.py'); open(f, 'w').write(py)
    r = subprocess.run([sys.executable, f], cwd=workdir, capture_output=True, text=True, timeout=60)
    line = [l for l in r.stdout.splitlines() if l.startswith('DTRESULT ')]
    if not line: return [(False, 'no result: ' + (r.stderr.strip().splitlines() or ['?'])[-1][:300])]
    res = json.loads(line[0][9:])
    return [(ok, '%s ;=> %s' % (e, w) if ok else '%s gave %s, expected %s' % (e, v, w)) for (ok, v), (e, w) in zip(res, checks)]

def run_sketch(code, workdir, frames, needs_frame):
    d = workdir; b = os.path.join(d, 'build'); os.makedirs(b, exist_ok=True)
    py, err = transpile(code)
    if err: return None, '', 'transpile: ' + err
    hook = ('_dt_draw = globals().get("draw")\n'
            'def draw():\n'
            '    if _dt_draw: _dt_draw()\n'
            '    if frame_count >= %d:\n'
            '        %s\n'
            '        import os as _dto; _dto._exit(0)\n') % (frames, 'save_frame("dt.png")' if needs_frame else 'pass')
    py = re.sub(r'^(_scm_wrap\(globals\(\)\))\s*$', hook.replace('\\', '\\\\') + r'\1', py, count=1, flags=re.M)
    open(os.path.join(b, 'dt.py'), 'w').write(py)
    try:
        r = subprocess.run([sys.executable, 'dt.py'], cwd=b, capture_output=True, text=True, timeout=frames // 2 + 30)
        out = r.stdout + r.stderr
    except subprocess.TimeoutExpired as e:
        out = ((e.stdout or b'').decode(errors='replace') if isinstance(e.stdout, bytes) else (e.stdout or '')) + '\n[timed out]'
    img = None
    for p in (os.path.join(d, 'dt.png'), os.path.join(b, 'dt.png')):
        if os.path.exists(p): img = read_png(p); break
    return img, out, None

def check_sketch(code, workdir):
    frames = 30; exp = []
    for line in code.splitlines():
        s = line.strip()
        m = re.match(r';;\s*frames\s+(\d+)', s)
        if m: frames = int(m.group(1)); continue
        for pat, kind in ((r';;\s*pixel\s+(\d+)\s+(\d+)\s*=>\s*(\d+)\s+(\d+)\s+(\d+)', 'pixel'),
                          (r';;\s*region\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+has\s+(\d+)\s+(\d+)\s+(\d+)', 'region'),
                          (r';;\s*brightest\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+between\s+(\d+)\s+(\d+)', 'brightest'),
                          (r';;\s*output:\s*(.+)', 'output'),
                          (r';;\s*file\s+(\S+)\s+contains\s+(.+)', 'file')):
            m = re.match(pat, s)
            if m: exp.append((kind, m.groups(), s[2:].strip()))
    needs_frame = any(k in ('pixel', 'region', 'brightest') for k, _, _ in exp)
    img, out, err = run_sketch(code, workdir, frames, needs_frame)
    if err: return [(False, err)]
    res = []
    for kind, g, text in exp:
        if kind == 'pixel':
            x, y, *want = map(int, g); c = px(img, x, y) if img else None
            res.append((close(c, want), text if close(c, want) else '%s, got %s' % (text, c)))
        elif kind == 'region':
            x, y, w, h, *want = map(int, g); ok = False
            if img:
                ok = any(close(px(img, xx, yy), want) for yy in range(y, y + h) for xx in range(x, x + w))
            res.append((ok, text if ok else text + (', not found' if img else ', no frame saved')))
        elif kind == 'brightest':
            x, y, w, h, lo, hi = map(int, g); best = None
            if img:
                vals = [max(c) for c in (px(img, xx, yy) for yy in range(y, y + h) for xx in range(x, x + w)) if c]
                best = max(vals) if vals else None
            ok = best is not None and lo <= best <= hi
            res.append((ok, text if ok else '%s, got %s' % (text, best)))
        elif kind == 'output':
            ok = g[0].strip() in out
            res.append((ok, text if ok else '%s, printed: %s' % (text, out.strip()[-200:])))
        elif kind == 'file':
            p = next((q for q in (os.path.join(workdir, g[0]), os.path.join(workdir, 'build', g[0])) if os.path.exists(q)), os.path.join(workdir, g[0]))
            ok = os.path.exists(p) and g[1].strip() in open(p, errors='replace').read()
            res.append((ok, text if ok else text + (', file missing' if not os.path.exists(p) else ', text not found')))
    if not res: res.append((True, 'ran'))
    if not img and needs_frame:
        res.append((False, 'no frame saved: ' + ' | '.join(out.strip().splitlines()[-3:])))
    return res

def main():
    args = sys.argv[1:]; pat = None
    if args[:1] == ['-k']: pat = args[1]; args = args[2:]
    files = args or sorted(glob.glob(os.path.join(DOCS, '**', '*.md'), recursive=True))
    total = failed = 0
    for path in files:
        name = os.path.relpath(path, os.path.join(HERE, '..'))
        for n, (lineno, code) in enumerate(blocks(path)):
            label = '%s:%d' % (name, lineno)
            if pat and pat not in code and pat not in label: continue
            wd = os.path.join(ROOT, re.sub(r'\W', '_', label)); shutil.rmtree(wd, ignore_errors=True); os.makedirs(wd)
            if ';=>' in code:
                res = run_values(code, wd)
            elif re.search(r'^\s*;;\s*(pixel|region|output:|file|frames)', code, re.M):
                res = check_sketch(code, wd)
            else:
                _, err = transpile(code)
                res = [(err is None, 'transpiles' if err is None else 'transpile: ' + err)]
            known = re.search(r'^\s*;;\s*known difference', code, re.M) is not None
            for ok, msg in res:
                total += 1
                if known:
                    print(('FIXED ' if ok else 'KNOWN ') + label + '  ' + msg)
                else:
                    failed += 0 if ok else 1
                    print(('ok    ' if ok else 'FAIL  ') + label + '  ' + msg)
    print('%d of %d checks passed' % (total - failed, total))
    sys.exit(1 if failed else 0)

main()
