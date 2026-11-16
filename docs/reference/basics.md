# SchemeMode basics

SchemeMode runs Processing sketches written in Scheme (R7RS). Every Processing function is available under its Scheme name: the Java name in lowercase with dashes, so `textSize` becomes `text-size` and `mouseX` becomes `mouse-x`.

Every example on this page is checked by `scripts/doctest.py`. A comment like `;=> 3` means the expression gives that value; `;; pixel 100 100 => 255 0 0` means that pixel has that color once the sketch runs.

## Conversions and math

Processing's conversion functions follow Java's rules: `int` truncates, `byte` wraps around, `str` writes booleans as `true` and `false`.

```scheme
(int 3.7)              ;=> 3
(int "42")             ;=> 42
(float "2.5")          ;=> 2.5
(str #t)               ;=> "true"
(byte 200)             ;=> -56
(nf 7 3)               ;=> "007"
(constrain 15 0 10)    ;=> 10
(dist 0 0 3 4)         ;=> 5.0
(lerp 0 10 0.5)        ;=> 5.0
```

## Array helpers

Processing's array functions work on Scheme lists and vectors.

```scheme
(expand (list 1 2))                 ;=> (1 2 0 0)
(shorten (list 1 2 3))              ;=> (1 2)
(splice (list 1 4) (list 2 3) 1)    ;=> (1 2 3 4)
```

## Drawing

A sketch defines `setup` and `draw`, just like in Processing.

```scheme
(define (setup) (size 200 200))
(define (draw)
  (background 255)
  (no-stroke)
  (fill 255 0 0)
  (rect 50 50 100 100))
;; pixel 100 100 => 255 0 0
;; pixel 10 10 => 255 255 255
```

## Clipping

`clip` limits drawing to a rectangle until `no-clip`.

```scheme
(define (setup) (size 200 200))
(define (draw)
  (background 255)
  (clip 50 50 100 100)
  (no-stroke)
  (fill 0 0 255)
  (rect 0 0 200 200)
  (no-clip))
;; pixel 100 100 => 0 0 255
;; pixel 20 20 => 255 255 255
```

## A fixed coordinate system

`window-ratio` lets you draw in your own coordinates; the drawing is scaled to fill the window.

```scheme
(define (setup)
  (size 400 200)
  (window-ratio 100 50))
(define (draw)
  (background 255)
  (no-stroke)
  (fill 0 160 0)
  (rect 0 0 50 50))
;; pixel 100 100 => 0 160 0
;; pixel 300 100 => 255 255 255
```

## Text

```scheme
(define (setup) (size 200 100))
(define (draw)
  (background 255)
  (fill 0)
  (text-size 40)
  (text "Hi" 20 70))
;; region 20 30 80 45 has 0 0 0
```

## Printing

`println` works like Processing's, with spaces between the values.

```scheme
(define (setup) (println "hello" (+ 1 2)))
;; output: hello 3
```

## Recording to a file

Everything drawn between `begin-record` and `end-record` is also written to an SVG or PDF file in the sketch folder.

```scheme
(define (setup)
  (size 200 200)
  (begin-record SVG "drawing.svg")
  (fill 255 0 0)
  (ellipse 100 100 80 80)
  (end-record))
;; file drawing.svg contains <ellipse
```

## 3D shapes

Vertices take a third coordinate in `P3D`.

```scheme
(define (setup) (size 200 200 P3D))
(define (draw)
  (background 0)
  (no-stroke)
  (fill 255 200 0)
  (begin-shape TRIANGLES)
  (vertex 100 40 0)
  (vertex 40 160 0)
  (vertex 160 160 0)
  (end-shape))
;; pixel 100 120 => 255 200 0
```
