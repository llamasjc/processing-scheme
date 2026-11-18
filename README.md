# SchemeMode

Write Processing sketches in Scheme (R7RS), right inside the Processing 4 editor.

```scheme
(define (setup)
  (size 400 400))

(define (draw)
  (background 30)
  (no-stroke)
  (fill 255 200 0)
  (ellipse mouse-x mouse-y 80 80))
```

Every Processing function is there under its Scheme name: `textSize` is `text-size`, `mouseX` is `mouse-x`. Sketches run on [libprocessing](https://github.com/processing/libprocessing), the new Processing engine written in Rust, through its Python package, mewnala.

## What works

Drawing, text, images, pixels, shapes, 3D with lights and materials, offscreen graphics, filters, shaders, clipping, recording to SVG, PDF and DXF, tables, JSON, XML, and the rest of the core Processing API. The editor highlights, checks your code as you type, and jumps to definitions.

The reference pages in [`docs/reference`](docs/reference) double as tests: every example in them is run by `scripts/doctest.py`, which checks the values, pixels, output and files each one claims. Where SchemeMode still differs from Processing, the page says so.

## Installing

1. Install Python 3.14.
2. Install mewnala from [the libprocessing fork's releases](https://github.com/llamasjc/libprocessing/releases), picking the wheel for your system:
   `pip install --user mewnala-0.0.8+fork-...whl`
3. Clone this repository into the `modes` folder of your Processing sketchbook, as `SchemeMode`:
   `git clone https://github.com/llamasjc/processing-scheme ~/sketchbook/modes/SchemeMode`
4. Restart Processing and pick Scheme from the mode menu.

## Exporting

File > Export Application builds a Linux AppImage that runs on its own, with Python and mewnala inside. `scripts/export.py` builds the same for Windows and macOS.

## Running the tests

```bash
source scripts/dev.env
scripts/regress.sh          # transpiler tests
python3 scripts/doctest.py  # every example in docs/reference
```
