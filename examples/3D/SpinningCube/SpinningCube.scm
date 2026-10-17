; A lit cube spinning in the middle of the window.
(import (processing))

(define angle 0.0)

(define (setup)
  (size 800 600 P3D))

(define (draw)
  (background 20)
  (lights)
  (set! angle (+ angle 0.01))
  (translate (/ width 2) (/ height 2) 0)
  (rotate-x angle)
  (rotate-y (* angle 1.3))
  (rotate-z (* angle 0.7))
  (no-stroke)
  (fill 100 180 255)
  (box 200))
