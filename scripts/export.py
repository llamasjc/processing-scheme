#!/usr/bin/env python3
"""Export a SchemeMode sketch as a self-contained app.

  scripts/export.py SKETCH_FOLDER [--out FOLDER] [--wheel PATH]

Linux: one AppImage with its own Python 3.14, the fork's mewnala and the sketch.
Downloads (Python, appimagetool) are cached in ~/.cache/schememode.
"""
import json, os, shutil, stat, struct, subprocess, sys, tarfile, urllib.request, zlib

CACHE = os.path.expanduser('~/.cache/schememode')
FALLBACK_PY = ('https://github.com/astral-sh/python-build-standalone/releases/download/20260114/'
               'cpython-3.14.2%2B20260114-x86_64-unknown-linux-gnu-install_only_stripped.tar.gz')
APPIMAGETOOL = 'https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage'

def say(*a): print('[export]', *a, flush=True)

def fetch(url, dest):
    if os.path.exists(dest): return dest
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    say('downloading', url.rsplit('/', 1)[-1])
    tmp = dest + '.part'
    with urllib.request.urlopen(url) as r, open(tmp, 'wb') as f: shutil.copyfileobj(r, f)
    os.rename(tmp, dest)
    return dest

def python_url():
    try:
        req = urllib.request.Request('https://api.github.com/repos/astral-sh/python-build-standalone/releases/latest',
                                     headers={'Accept': 'application/vnd.github+json'})
        rel = json.load(urllib.request.urlopen(req, timeout=15))
        for a in rel.get('assets', []):
            n = a['name']
            if n.startswith('cpython-3.14.') and n.endswith('-x86_64-unknown-linux-gnu-install_only_stripped.tar.gz'):
                return a['browser_download_url']
    except Exception as e:
        say('could not ask GitHub for the latest Python build (%s); using a known one' % e)
    return FALLBACK_PY

def icon_png(path, size=256):
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            d = ((x - size / 2) ** 2 + (y - size / 2) ** 2) ** 0.5
            row += bytes((70, 110, 220, 255)) if d < size * 0.42 else bytes((0, 0, 0, 0))
        rows.append(bytes(row))
    def chunk(t, b): return struct.pack('>I', len(b)) + t + b + struct.pack('>I', zlib.crc32(t + b) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(png)

def transpile(sketch, name):
    tx = os.environ.get('TX')
    if not tx: sys.exit('run scm first (TX is not set)')
    tabs = sorted(f for f in os.listdir(sketch) if f.endswith('.scm'))
    main = name + '.scm'
    order = [main] + [t for t in tabs if t != main] if main in tabs else tabs
    src = '\n'.join(open(os.path.join(sketch, t)).read() for t in order)
    r = subprocess.run(['java', '-cp', tx, 'Tx'], input=src, capture_output=True, text=True)
    if r.returncode or not r.stdout.strip(): sys.exit('transpile failed:\n' + r.stderr[-800:])
    return r.stdout

def export_linux(sketch, out, wheel):
    name = os.path.basename(os.path.normpath(sketch))
    appdir = os.path.join(CACHE, 'build', name + '.AppDir')
    shutil.rmtree(appdir, ignore_errors=True)
    os.makedirs(appdir)
    pytar = fetch(python_url(), os.path.join(CACHE, 'python-linux-x86_64.tar.gz'))
    say('unpacking Python')
    with tarfile.open(pytar) as t: t.extractall(os.path.join(appdir, 'usr'), filter='data')
    py = os.path.join(appdir, 'usr', 'python', 'bin', 'python3')
    say('installing mewnala from', wheel)
    subprocess.run([py, '-m', 'pip', 'install', '--quiet', '--no-deps', '--no-index', '--no-warn-script-location', wheel], check=True)
    sk = os.path.join(appdir, 'usr', 'sketch', name); b = os.path.join(sk, 'build')
    os.makedirs(b)
    open(os.path.join(b, name + '.py'), 'w').write(transpile(sketch, name))
    data = os.path.join(sketch, 'data')
    if os.path.isdir(data):
        shutil.copytree(data, os.path.join(sk, 'data'))
        shutil.copytree(data, os.path.join(b, 'assets'))
    run = os.path.join(appdir, 'AppRun')
    open(run, 'w').write('#!/bin/sh\n'
                         'HERE="$(dirname "$(readlink -f "$0")")"\n'
                         'export SCHEME_OUTPUT_DIR="${SCHEME_OUTPUT_DIR:-$(dirname "${APPIMAGE:-$HERE}")}"\n'
                         'cd "$HERE/usr/sketch/%s/build" || exit 1\n'
                         'exec "$HERE/usr/python/bin/python3" "%s.py" "$@"\n' % (name, name))
    os.chmod(run, 0o755)
    open(os.path.join(appdir, name + '.desktop'), 'w').write(
        '[Desktop Entry]\nType=Application\nName=%s\nExec=AppRun\nIcon=%s\nCategories=Graphics;\nTerminal=false\n' % (name, name))
    icon_png(os.path.join(appdir, name + '.png'))
    tool = fetch(APPIMAGETOOL, os.path.join(CACHE, 'appimagetool-x86_64.AppImage'))
    os.chmod(tool, os.stat(tool).st_mode | stat.S_IEXEC)
    os.makedirs(out, exist_ok=True)
    target = os.path.join(out, name + '-x86_64.AppImage')
    say('building', target)
    env = dict(os.environ, ARCH='x86_64', APPIMAGE_EXTRACT_AND_RUN='1')
    r = subprocess.run([tool, '--no-appstream', appdir, target], env=env, capture_output=True, text=True)
    if r.returncode or not os.path.exists(target): sys.exit('appimagetool failed:\n' + (r.stderr or r.stdout)[-800:])
    say('done:', target, '(%.1f MB)' % (os.path.getsize(target) / 1e6))
    return target

def main():
    a = sys.argv[1:]
    if not a or a[0].startswith('-'): sys.exit(__doc__)
    sketch = os.path.abspath(a[0])
    out = a[a.index('--out') + 1] if '--out' in a else os.path.join(sketch, 'export')
    wheel = a[a.index('--wheel') + 1] if '--wheel' in a else os.path.expanduser('~/wheels/fork/mewnala-0.0.8-cp314-cp314-linux_x86_64.whl')
    if not os.path.exists(wheel): sys.exit('no mewnala wheel at ' + wheel)
    export_linux(sketch, os.path.abspath(out), wheel)

main()
