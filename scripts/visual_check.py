#!/usr/bin/env python3
"""SchemeMode visual checks: run small sketches, save a frame, and analyze its pixels."""
import os, sys, subprocess, zlib, struct, re, shutil

ROOT = os.path.expanduser('~/sketchbook/.scm_vcheck')
TX = os.environ.get('TX')
if not TX:
    sys.exit('run scm first (TX is not set)')

def read_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos, idat, w = 8, b'', 0
    while pos < len(data):
        n, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
            assert depth == 8 and ctype in (2, 6), 'unsupported PNG'
            bpp = 4 if ctype == 6 else 3
        elif kind == b'IDAT':
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride = w * bpp
    out = bytearray(h * stride)
    prev = bytearray(stride)
    i = 0
    for y in range(h):
        f = raw[i]; i += 1
        row = bytearray(raw[i:i + stride]); i += stride
        for x in range(stride):
            a = row[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1: row[x] = (row[x] + a) & 255
            elif f == 2: row[x] = (row[x] + b) & 255
            elif f == 3: row[x] = (row[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                row[x] = (row[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out[y * stride:(y + 1) * stride] = row
        prev = row
    return w, h, bpp, out

def px(img, x, y):
    w, h, bpp, d = img
    k = (y * w + x) * bpp
    return d[k], d[k + 1], d[k + 2]

def lum(c): return (c[0] * 299 + c[1] * 587 + c[2] * 114) / 1000.0

def run_sketch(name, src, secs=25):
    d = os.path.join(ROOT, name); b = os.path.join(d, 'build')
    shutil.rmtree(d, ignore_errors=True); os.makedirs(b)
    tx = subprocess.run(['java', '-cp', TX, 'Tx'], input=src, capture_output=True, text=True)
    if tx.returncode or not tx.stdout.strip():
        return None, 'transpile failed: ' + tx.stderr[:600]
    open(os.path.join(b, name + '.py'), 'w').write(tx.stdout)
    wrapper = ("import faulthandler, sys\n"
               "faulthandler.dump_traceback_later(%d, exit=True)\n"
               "sys.argv = ['%s.py']\n"
               "exec(compile(open('%s.py').read(), '%s.py', 'exec'))\n" % (secs - 4, name, name, name))
    try:
        r = subprocess.run([sys.executable, '-c', wrapper], cwd=b, capture_output=True, text=True, timeout=secs)
        out = r.stdout + r.stderr
    except subprocess.TimeoutExpired as e:
        out = (e.stdout or b'').decode(errors='replace') + (e.stderr or b'').decode(errors='replace') + '\n[timed out]'
    for shot in (os.path.join(d, 'shot.png'), os.path.join(b, 'shot.png')):
        if os.path.exists(shot):
            return read_png(shot), out
    return None, out

results = []
def report(name, ok, detail, out=None):
    results.append((name, ok))
    print(('PASS ' if ok else 'FAIL ') + name + ': ' + detail)
    if not ok and out:
        tail = [l for l in out.strip().splitlines() if l.strip()][-14:]
        print('    ' + '\n    '.join(tail))

def box_stats(img):
    w, h, bpp, d = img
    def is_box(x, y):
        if not (0 <= x < w and 0 <= y < h): return False
        c = px(img, x, y)
        return abs(c[0] - 30) + abs(c[1] - 30) + abs(c[2] - 30) > 30
    sides = {'left': [], 'right': []}
    ys = []
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if not is_box(x, y): continue
            ys.append(y)
            if all(is_box(x + dx, y + dy) for dx, dy in ((3, 0), (-3, 0), (0, 3), (0, -3))):
                sides['left' if x < w // 2 else 'right'].append(lum(px(img, x, y)))
    def sd(v):
        if len(v) < 2: return 0.0
        m = sum(v) / len(v); return (sum((a - m) ** 2 for a in v) / len(v)) ** 0.5
    cy = sum(ys) / len(ys) if ys else -1
    return cy, len(sides['left']), len(sides['right']), sd(sides['left']), sd(sides['right'])

CAM = '''
(define (setup) (size 500 300 P3D))
(define (draw)
  (background 30)
  %s
  (lights)
  (push-matrix) (translate 150 150 0) (rotate-y 0.6) (rotate-x 0.4) (no-stroke) (fill 200 120 60) (box 100) (pop-matrix)
  (no-lights)
  (push-matrix) (translate 350 150 0) (rotate-y 0.6) (rotate-x 0.4) (fill 200 120 60) (box 100) (pop-matrix)
  (when (= frame-count 20) (save-frame "shot.png") (exit)))
'''
off, out_off = run_sketch('cam_off', CAM % '')
on, out_on = run_sketch('cam_on', CAM % '(begin-camera) (camera) (rotate-x -0.6) (end-camera)')
if off and on:
    cy0, l0, r0, ls0, rs0 = box_stats(off)
    cy1 = box_stats(on)[0]
    report('camera-tilt', abs(cy1 - cy0) > 8, 'box center y %.0f without the tilt, %.0f with it' % (cy0, cy1))
    report('no-lights', l0 > 200 and r0 > 200 and ls0 > 4 and rs0 < 2,
           'brightness spread: left (lit) %.1f, right (no-lights) %.1f; box pixels %d / %d' % (ls0, rs0, l0, r0))
else:
    report('camera-tilt', False, 'no frame saved', out_on if not on else out_off)

SM = '''
(define (setup) (size 200 200) %s)
(define (draw)
  (background 255) (no-stroke) (fill 0)
  (translate 100 100) (rotate 0.3) (rect -50 -50 100 100)
  (when (= frame-count 5) (save-frame "shot.png") (exit)))
'''
def soft_edges(img):
    w, h, bpp, d = img
    return sum(1 for y in range(h) for x in range(w) if 30 < lum(px(img, x, y)) < 225)
s1, o1 = run_sketch('smooth_on', SM % '(smooth 4)')
s0, o0 = run_sketch('smooth_off', SM % '(no-smooth)')
if s1 and s0:
    a, b = soft_edges(s1), soft_edges(s0)
    report('smooth', a > 50 and a > 3 * max(b, 1), 'in-between edge pixels: %d with smooth, %d with no-smooth' % (a, b))
else:
    report('smooth', False, 'no frame saved', o1 if not s1 else o0)

FIL = '''
(define (setup) (size 300 200))
(define (draw)
  (background 255) (no-stroke)
  (fill 220 60 60) (ellipse 100 100 80 80) (fill 60 120 220) (ellipse 200 100 80 80)
  (filter BLUR 4) (filter GRAY)
  (when (= frame-count 1) (println "frame 1 done"))
  (when (= frame-count 10) (save-frame "shot.png") (exit)))
'''
f, of = run_sketch('filter', FIL)
if 'frame 1 done' not in of:
    report('filter', False, 'the first frame never finished (hang or error)', of)
elif not f:
    report('filter', False, 'no frame saved', of)
else:
    w, h, bpp, d = f
    color = max(abs(px(f, x, y)[0] - px(f, x, y)[1]) + abs(px(f, x, y)[1] - px(f, x, y)[2]) for y in range(0, h, 3) for x in range(0, w, 3))
    ramp = sum(1 for x in range(40, 100) if 30 < lum(px(f, x, 100)) < 225)
    report('filter', color < 12 and ramp > 6, 'max color left after GRAY %d, blurred edge width %d px' % (color, ramp))

PIX = '''
(define buf (make-vector 600 0))
(define (setup) (size 600 300) (window-ratio 200 100))
(define (draw)
  (background 230) (no-stroke) (fill 40 90 200) (rect 0 0 200 100)
  LOAD
  (do ((i 0 (+ i 1))) ((= i 600)) (vector-set! TARGET i (color 255 0 0)))
  UPDATE
  (when (= frame-count 120) (println "t0" (millis)))
  (when (= frame-count 240) (save-frame "shot.png"))
  (when (= frame-count 300) (println "t1" (millis)) (exit)))
'''
def measured_fps(out):
    t0 = re.findall(r't0 (\d+)', out); t1 = re.findall(r't1 (\d+)', out)
    return 180000.0 / (int(t1[0]) - int(t0[0])) if t0 and t1 and int(t1[0]) > int(t0[0]) else 0.0
fb = fp = 0.0; p = None; op = ''
for attempt in range(2):
    base, ob = run_sketch('pixels_base', PIX.replace('LOAD', '').replace('UPDATE', '').replace('TARGET', 'buf'), secs=40)
    fb = max(fb, measured_fps(ob))
    shot, out = run_sketch('pixels', PIX.replace('LOAD', '(load-pixels)').replace('UPDATE', '(update-pixels)').replace('TARGET', 'pixels'), secs=40)
    if measured_fps(out) >= fp: fp, p, op = measured_fps(out), shot, out
top = px(p, 300, 0) if p else None
ok = fp > 0 and fb > 0 and fp >= 0.8 * fb and top is not None and top[0] > 200 and top[1] < 60
report('pixels', ok, '%.0f fps with pixels, %.0f fps with the same loop into a plain vector; top row %s' % (fp, fb, top), op)

print('%d of %d passed' % (sum(1 for _, k in results if k), len(results)))
