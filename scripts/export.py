#!/usr/bin/env python3
"""Export a SchemeMode sketch as a self-contained app.

  scripts/export.py SKETCH_FOLDER [--target linux|windows|macos|all] [--out FOLDER]
                    [--wheel PATH] [--program FILE.py] [--name NAME]

linux:   one AppImage (standalone Python 3.14, the fork's mewnala, the sketch)
windows: name-windows-x64.zip with python/, the sketch and run.bat
macos:   name-macos-arm64.zip with name.app
Without --wheel, Linux uses the newest local fork wheel; Windows and macOS download theirs
from the newest Release of llamasjc/libprocessing. Downloads are cached in ~/.cache/schememode.
"""
import glob, json, os, re, shutil, stat, struct, subprocess, sys, tarfile, urllib.request, zipfile, zlib

CACHE = os.path.expanduser('~/.cache/schememode')
FORK = 'llamasjc/libprocessing'
APPIMAGETOOL = 'https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage'
TRIPLES = {'linux': 'x86_64-unknown-linux-gnu', 'windows': 'x86_64-pc-windows-msvc', 'macos': 'aarch64-apple-darwin'}
WHEEL_TAG = {'linux': ('manylinux', 'x86_64'), 'windows': ('win_amd64', ''), 'macos': ('macosx', 'arm64')}

def say(*a): print('[export]', *a, flush=True)

def gh_json(url):
    req = urllib.request.Request(url, headers={'Accept': 'application/vnd.github+json'})
    return json.load(urllib.request.urlopen(req, timeout=20))

def fetch(url, dest):
    if os.path.exists(dest): return dest
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    say('downloading', url.rsplit('/', 1)[-1])
    tmp = dest + '.part'
    with urllib.request.urlopen(url) as r, open(tmp, 'wb') as f: shutil.copyfileobj(r, f)
    os.rename(tmp, dest)
    return dest

def python_url(minor, target):
    triple = TRIPLES[target]
    rel = gh_json('https://api.github.com/repos/astral-sh/python-build-standalone/releases/latest')
    for a in rel.get('assets', []):
        n = a['name']
        if n.startswith('cpython-3.%d.' % minor) and n.endswith('-%s-install_only_stripped.tar.gz' % triple):
            return a['browser_download_url']
    sys.exit('no standalone Python 3.%d for %s in the latest python-build-standalone release' % (minor, target))

def release_wheel(target):
    plat, arch = WHEEL_TAG[target]
    rel = gh_json('https://api.github.com/repos/%s/releases/latest' % FORK)
    found = []
    for a in rel.get('assets', []):
        n = a['name']
        m = re.match(r'mewnala-.+-cp3(\d+)-cp3\d+-(.+)\.whl$', n)
        if m and plat in m.group(2) and arch in m.group(2):
            found.append((int(m.group(1)), n, a['browser_download_url']))
    if not found: sys.exit('no %s wheel in the latest release of %s' % (target, FORK))
    found.sort()
    pick = next((f for f in found if f[0] == 14), found[-1])
    return pick[0], fetch(pick[2], os.path.join(CACHE, 'wheels', pick[1]))

def wheel_minor(path):
    m = re.search(r'-cp3(\d+)-', os.path.basename(path))
    return int(m.group(1)) if m else 14

def local_wheel():
    found = sorted(glob.glob(os.path.expanduser('~/wheels/fork/mewnala-*-cp314-cp314-linux_x86_64.whl')), key=os.path.getmtime)
    return found[-1] if found else None

def icon_png(path, size=256):
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            d = ((x - size / 2) ** 2 + (y - size / 2) ** 2) ** 0.5
            row += bytes((70, 110, 220, 255)) if d < size * 0.42 else bytes((0, 0, 0, 0))
        rows.append(bytes(row))
    def chunk(t, b): return struct.pack('>I', len(b)) + t + b + struct.pack('>I', zlib.crc32(t + b) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b''))

def transpile(sketch, name):
    tx = os.environ.get('TX')
    if not tx: sys.exit('run scm first (TX is not set), or pass --program')
    tabs = sorted(f for f in os.listdir(sketch) if f.endswith('.scm'))
    main = name + '.scm'
    order = [main] + [t for t in tabs if t != main] if main in tabs else tabs
    src = '\n'.join(open(os.path.join(sketch, t)).read() for t in order)
    r = subprocess.run(['java', '-cp', tx, 'Tx'], input=src, capture_output=True, text=True)
    if r.returncode or not r.stdout.strip(): sys.exit('transpile failed:\n' + r.stderr[-800:])
    return r.stdout

def unpack_python(target, minor, dest):
    tarpath = fetch(python_url(minor, target), os.path.join(CACHE, 'python-3.%d-%s.tar.gz' % (minor, target)))
    with tarfile.open(tarpath) as t: t.extractall(dest, filter='data')
    return os.path.join(dest, 'python')

def put_sketch(sketch, name, program_text, dest):
    b = os.path.join(dest, 'build'); os.makedirs(b)
    open(os.path.join(b, name + '.py'), 'w').write(program_text)
    data = os.path.join(sketch, 'data')
    if os.path.isdir(data):
        shutil.copytree(data, os.path.join(dest, 'data'))
        shutil.copytree(data, os.path.join(b, 'assets'))

def zip_dir(src, zip_path):
    if os.path.exists(zip_path): os.remove(zip_path)
    base = os.path.dirname(src)
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
        for root, dirs, files in os.walk(src):
            for f in files + [d for d in dirs if os.path.islink(os.path.join(root, d))]:
                full = os.path.join(root, f)
                z.write(full, os.path.relpath(full, base))
    return zip_path

def export_linux(sketch, out, name, program_text, wheel):
    appdir = os.path.join(CACHE, 'build', name + '.AppDir')
    shutil.rmtree(appdir, ignore_errors=True); os.makedirs(appdir)
    say('linux: unpacking Python 3.%d' % wheel_minor(wheel))
    py = os.path.join(unpack_python('linux', wheel_minor(wheel), os.path.join(appdir, 'usr')), 'bin', 'python3')
    say('linux: installing', os.path.basename(wheel))
    subprocess.run([py, '-m', 'pip', 'install', '--quiet', '--no-deps', '--no-index', '--no-warn-script-location', wheel], check=True)
    put_sketch(sketch, name, program_text, os.path.join(appdir, 'usr', 'sketch', name))
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
    target = os.path.join(out, name + '-x86_64.AppImage')
    env = dict(os.environ, ARCH='x86_64', APPIMAGE_EXTRACT_AND_RUN='1')
    r = subprocess.run([tool, '--no-appstream', appdir, target], env=env, capture_output=True, text=True)
    if r.returncode or not os.path.exists(target): sys.exit('appimagetool failed:\n' + (r.stderr or r.stdout)[-800:])
    return target

def unpack_wheel(wheel, site):
    os.makedirs(site, exist_ok=True)
    with zipfile.ZipFile(wheel) as z: z.extractall(site)

def export_windows(sketch, out, name, program_text, wheel):
    minor = wheel_minor(wheel)
    stage = os.path.join(CACHE, 'build', name + '-windows'); shutil.rmtree(stage, ignore_errors=True)
    root = os.path.join(stage, name); os.makedirs(root)
    say('windows: unpacking Python 3.%d' % minor)
    unpack_python('windows', minor, root)
    say('windows: unpacking', os.path.basename(wheel))
    unpack_wheel(wheel, os.path.join(root, 'python', 'Lib', 'site-packages'))
    put_sketch(sketch, name, program_text, os.path.join(root, 'sketch'))
    with open(os.path.join(root, 'run.bat'), 'w', newline='\r\n') as f:
        f.write('@echo off\nset SCHEME_OUTPUT_DIR=%~dp0\ncd /d "%~dp0sketch\\build"\n"%~dp0python\\python.exe" "' + name + '.py" %*\n')
    return zip_dir(root, os.path.join(out, name + '-windows-x64.zip'))

def export_macos(sketch, out, name, program_text, wheel):
    minor = wheel_minor(wheel)
    stage = os.path.join(CACHE, 'build', name + '-macos'); shutil.rmtree(stage, ignore_errors=True)
    app = os.path.join(stage, name + '.app'); res = os.path.join(app, 'Contents', 'Resources')
    os.makedirs(os.path.join(app, 'Contents', 'MacOS')); os.makedirs(res)
    say('macos: unpacking Python 3.%d' % minor)
    unpack_python('macos', minor, res)
    say('macos: unpacking', os.path.basename(wheel))
    unpack_wheel(wheel, os.path.join(res, 'python', 'lib', 'python3.%d' % minor, 'site-packages'))
    put_sketch(sketch, name, program_text, os.path.join(res, 'sketch'))
    launcher = os.path.join(app, 'Contents', 'MacOS', name)
    open(launcher, 'w').write('#!/bin/sh\n'
                              'RES="$(cd "$(dirname "$0")/../Resources" && pwd)"\n'
                              'export SCHEME_OUTPUT_DIR="${SCHEME_OUTPUT_DIR:-$(cd "$RES/../../.." && pwd)}"\n'
                              'cd "$RES/sketch/build" || exit 1\n'
                              'exec "$RES/python/bin/python3" "%s.py" "$@"\n' % name)
    os.chmod(launcher, 0o755)
    open(os.path.join(app, 'Contents', 'Info.plist'), 'w').write(
        '<?xml version="1.0" encoding="UTF-8"?>\n<plist version="1.0"><dict>\n'
        '<key>CFBundleName</key><string>%s</string>\n<key>CFBundleExecutable</key><string>%s</string>\n'
        '<key>CFBundleIdentifier</key><string>org.schememode.%s</string>\n<key>CFBundlePackageType</key><string>APPL</string>\n'
        '</dict></plist>\n' % (name, name, re.sub(r'[^A-Za-z0-9.-]', '-', name)))
    return zip_dir(app, os.path.join(out, name + '-macos-arm64.zip'))

def main():
    a = sys.argv[1:]
    if not a or a[0].startswith('-'): sys.exit(__doc__)
    opt = lambda k, d=None: a[a.index(k) + 1] if k in a else d
    sketch = os.path.abspath(a[0])
    out = os.path.abspath(opt('--out', os.path.join(sketch, 'export'))); os.makedirs(out, exist_ok=True)
    name = opt('--name') or os.path.basename(os.path.normpath(sketch))
    program = opt('--program')
    program_text = open(program).read() if program else transpile(sketch, name)
    targets = ['linux', 'windows', 'macos'] if opt('--target', 'linux') == 'all' else [opt('--target', 'linux')]
    for t in targets:
        if t not in TRIPLES: sys.exit('unknown target ' + t)
        wheel = opt('--wheel') if len(targets) == 1 and opt('--wheel') else None
        if wheel is None:
            wheel = local_wheel() if t == 'linux' else None
            if wheel is None: _, wheel = release_wheel(t)
        if not os.path.exists(wheel): sys.exit('no mewnala wheel at ' + wheel)
        made = {'linux': export_linux, 'windows': export_windows, 'macos': export_macos}[t](sketch, out, name, program_text, wheel)
        say('done:', made, '(%.1f MB)' % (os.path.getsize(made) / 1e6))

main()
