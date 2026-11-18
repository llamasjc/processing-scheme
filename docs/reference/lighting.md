# Lights and materials

SchemeMode draws with a physically based renderer, while Processing uses an older lighting model. The settings below are mapped so sketches look recognizably the same. Every example is checked by `scripts/doctest.py`, measuring the brightest pixel of the lit sphere.

## Matte by default

A lit surface has no highlight unless the material has a `specular` color and the lights have a `light-specular` color. Both default to black, as in Processing, so `specular` alone changes nothing.

```scheme
(define (setup) (size 200 200 P3D))
(define (draw)
  (background 0) (no-stroke) (fill 128)
  (specular 255)
  (directional-light 255 255 255 -0.4 -0.4 -1)
  (translate 100 100 0) (sphere 60))
;; brightest 0 0 200 200 between 180 215
```

## Highlights

With both set, a highlight appears. `shininess` makes it smaller and more intense.

```scheme
(define (setup) (size 200 200 P3D))
(define (draw)
  (background 0) (no-stroke) (fill 128)
  (specular 255) (light-specular 255 255 255)
  (directional-light 255 255 255 -0.4 -0.4 -1)
  (translate 100 100 0) (sphere 60))
;; brightest 0 0 200 200 between 230 255
```

```scheme
(define (setup) (size 200 200 P3D))
(define (draw)
  (background 0) (no-stroke) (fill 128)
  (specular 255) (light-specular 255 255 255) (shininess 100)
  (directional-light 255 255 255 -0.4 -0.4 -1)
  (translate 100 100 0) (sphere 60))
;; brightest 0 0 200 200 between 248 255
```

## Point lights and falloff

By default a point light doesn't dim with distance, like Processing's. `light-falloff` dims it by Processing's formula, `1 / (c + l*d + q*d*d)` at distance `d`.

```scheme
(define (setup) (size 200 200 P3D))
(define (draw)
  (background 0) (no-stroke) (fill 128)
  (point-light 255 255 255 100 100 300)
  (translate 100 100 0) (sphere 60))
;; brightest 0 0 200 200 between 190 245
```

```scheme
(define (setup) (size 200 200 P3D))
(define (draw)
  (background 0) (no-stroke) (fill 128)
  (light-falloff 1 0.01 0.0001)
  (point-light 255 255 255 100 100 300)
  (translate 100 100 0) (sphere 60))
;; brightest 0 0 200 200 between 50 95
```

## Not mapped

`ambient` (the material's ambient color) has no counterpart: in physically based lighting, ambient light reflects with the fill color. It is accepted, so sketches using it run, but it has no visible effect.
