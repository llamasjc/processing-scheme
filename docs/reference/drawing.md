# Drawing in detail

Every example on this page is checked by `scripts/doctest.py`.

## Clipping follows the current transform

Like Processing, `clip` uses the current coordinates, so a `translate` before it moves the clip rectangle too.

```scheme
(define (setup) (size 200 200))
(define (draw)
  (background 255)
  (translate 50 50)
  (clip 0 0 100 100)
  (no-stroke)
  (fill 0 0 255)
  (rect -50 -50 200 200)
  (no-clip))
;; pixel 60 60 => 0 0 255
;; pixel 20 20 => 255 255 255
```

## Translucent colors

A fill's alpha mixes it with what's underneath. Processing mixes in screen colors, so half-transparent red over white is (255, 127, 127). SchemeMode doesn't match this yet: the engine mixes in linear light, which makes translucent colors lighter.

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 255)
  (no-stroke)
  (fill 255 0 0 128)
  (rect 0 0 100 100))
;; pixel 50 50 => 255 127 127
;; known difference: the engine still blends in linear light, so this comes out as (255, 187, 187)
```

## push and pop restore the style

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 255)
  (no-stroke)
  (fill 0 0 255)
  (push)
  (fill 255 0 0)
  (pop)
  (rect 0 0 100 100))
;; pixel 50 50 => 0 0 255
```

## Shape modes

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 255)
  (no-stroke)
  (fill 0)
  (rect-mode CENTER)
  (rect 50 50 20 20))
;; pixel 50 50 => 0 0 0
;; pixel 35 35 => 255 255 255
```

## Color modes

```scheme
(define (setup) (size 100 100))
(define (draw)
  (color-mode HSB 360 100 100)
  (background 0 0 100)
  (no-stroke)
  (fill 0 100 100)
  (rect 0 0 50 100)
  (color-mode RGB 1.0)
  (fill 0 0 1)
  (rect 50 0 50 100))
;; pixel 25 50 => 255 0 0
;; pixel 75 50 => 0 0 255
```

## Filters

`filter` works on everything drawn so far, with Processing's results: GRAY uses Processing's weights, so pure red becomes (77, 77, 77).

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 255 0 0)
  (filter GRAY))
;; pixel 50 50 => 77 77 77
```

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 255)
  (filter INVERT))
;; pixel 50 50 => 0 0 0
```

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 100)
  (filter THRESHOLD))
;; pixel 50 50 => 0 0 0
```

## Blend modes

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 100 0 0)
  (blend-mode ADD)
  (no-stroke)
  (fill 0 100 0)
  (rect 0 0 100 100)
  (blend-mode BLEND))
;; pixel 50 50 => 100 100 0
```

## Offscreen graphics

A graphics buffer is drawn into with its own calls, then shown with `image`.

```scheme
(define pg #f)
(define (setup)
  (size 100 100)
  (set! pg (create-graphics 50 50)))
(define (draw)
  (begin-draw pg)
  (background pg 0 255 0)
  (end-draw pg)
  (background 255)
  (image pg 10 10))
;; pixel 30 30 => 0 255 0
;; pixel 80 80 => 255 255 255
```

## Pixels

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 255)
  (load-pixels)
  (do ((x 0 (+ x 1))) ((= x 100)) (vector-set! pixels (+ (* 5 100) x) (color 255 0 0)))
  (update-pixels))
;; pixel 50 5 => 255 0 0
;; pixel 50 6 => 255 255 255
```

`get` reads a pixel back as a color.

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 255)
  (no-stroke)
  (fill 255 0 0)
  (rect 0 0 100 100)
  (when (= frame-count 10) (println "red is" (int (red (get 50 50))))))
;; output: red is 255
```

## Saving

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 0 0 255)
  (when (= frame-count 5) (save-frame "frame.png")))
;; file frame.png contains PNG
```

```scheme
(define (setup)
  (size 100 100)
  (begin-record PDF "drawing.pdf")
  (rect 10 10 50 50)
  (end-record))
;; file drawing.pdf contains %PDF
```

Filters work on screen colors, as in Processing, so mid tones come out the same too.

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 128)
  (filter INVERT))
;; pixel 50 50 => 127 127 127
```

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 140)
  (filter THRESHOLD))
;; pixel 50 50 => 255 255 255
```

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 200)
  (filter POSTERIZE 2))
;; pixel 50 50 => 255 255 255
```

Blurring a black and white edge averages in screen colors too, as in Processing. The edge lies between pixels 49 and 50, so the two pixels on either side of it come out just below and just above mid gray.

```scheme
(define (setup) (size 100 100))
(define (draw)
  (background 255)
  (no-stroke)
  (fill 0)
  (rect 0 0 50 100)
  (filter BLUR 6))
;; pixel 49 50 => 112 112 112
;; pixel 50 50 => 143 143 143
```
