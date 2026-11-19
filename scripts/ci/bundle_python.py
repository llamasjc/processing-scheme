#!/usr/bin/env python3
"""Puts a standalone Python with the fork's mewnala into python/<plat>/.

    bundle_python.py <plat> <wheels dir> <out dir>

<plat> is linux-x86_64, macos-aarch64 or windows-x86_64. Picks the newest
CPython the fork has a mewnala wheel for, downloads that Python from
astral-sh/python-build-standalone (install_only_stripped, the small one),
unpacks it and installs the wheel into it without dependencies: mewnala only
needs IPython and Jupyter for notebooks, and sketches don't use those.
"""
import re, shutil, subprocess, sys, tarfile, tempfile, urllib.request
from pathlib import Path

plat, wheels, out = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
WHEEL_TAG = {"linux-x86_64": "manylinux", "macos-aarch64": "macosx", "windows-x86_64": "win_amd64"}[plat]
WHEEL_ARCH = {"linux-x86_64": "x86_64", "macos-aarch64": "arm64", "windows-x86_64": "amd64"}[plat]
TRIPLE = {"linux-x86_64": "x86_64-unknown-linux-gnu", "macos-aarch64": "aarch64-apple-darwin",
          "windows-x86_64": "x86_64-pc-windows-msvc"}[plat]

found = []
for w in wheels.glob("mewnala-*.whl"):
    # cp312-cp312 only: cp313t (free-threaded) builds don't match
    m = re.search(r"-cp3(\d+)-cp3\d+-", w.name)
    if m and WHEEL_TAG in w.name and WHEEL_ARCH in w.name:
        found.append((int(m.group(1)), w))
if not found:
    sys.exit(f"no mewnala wheel for {plat} in {wheels}: {[w.name for w in wheels.glob('*.whl')]}")
minor, wheel = max(found)
print(f"{plat}: {wheel.name} (Python 3.{minor})")

# the latest release has well over a thousand files, so page through them
def gh_api(path):
    return subprocess.run(["gh", "api", "--paginate", path, "-q", ".[] | [.name, .browser_download_url] | @tsv"]
                          if "assets" in path else ["gh", "api", path, "-q", ".id"],
                          capture_output=True, text=True, check=True).stdout
rid = gh_api("repos/astral-sh/python-build-standalone/releases/latest").strip()
rows = [l.split("\t") for l in gh_api(f"repos/astral-sh/python-build-standalone/releases/{rid}/assets?per_page=100").splitlines() if l]
pat = re.compile(rf"^cpython-3\.{minor}\.\d+\+\d+-{re.escape(TRIPLE)}-install_only_stripped\.tar\.gz$")
assets = [{"name": n, "url": u} for n, u in rows if pat.match(n)]
if not assets:
    sys.exit(f"no python-build-standalone 3.{minor} build for {TRIPLE}")
asset = assets[0]
print(f"python: {asset['name']}")

dest = out / plat
if dest.exists():
    shutil.rmtree(dest)
with tempfile.TemporaryDirectory() as tmp:
    tgz = Path(tmp) / asset["name"]
    urllib.request.urlretrieve(asset["url"], tgz)
    with tarfile.open(tgz) as t:
        try:
            t.extractall(tmp, filter="tar")
        except TypeError:  # Python before 3.11.4 has no filter argument
            t.extractall(tmp)
    shutil.move(str(Path(tmp) / "python"), dest)

exe = dest / ("python.exe" if plat.startswith("windows") else "bin/python3")
subprocess.run([str(exe), "-m", "pip", "install", "--no-deps", "--no-compile",
                "--disable-pip-version-check", str(wheel)], check=True)
# what a sketch never uses: tests, the IDLE editor, tkinter's Tcl/Tk
for junk in ("lib/python3.%d/test" % minor, "Lib/test", "lib/python3.%d/idlelib" % minor, "Lib/idlelib"):
    shutil.rmtree(dest / junk, ignore_errors=True)
print(f"done: {exe}")
