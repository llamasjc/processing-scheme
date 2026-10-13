(define (a1) (map (lambda (x) (* x 2)) (list 1 2 3)))
(define (a2) (map 5 0 10 0 100))
(define (a3) (map + (list 1 2) (list 10 20)))
(define (a4) (list (lerp 0 10 0.5) (constrain 15 0 10) (constrain -5 0 10) (norm 5 0 10)))
(define (a5) (list (dist 0 0 3 4) (mag 3 4) (dist 0 0 0 1 2 2)))
(define (a6) (list (round (degrees 3.141592653589793)) (round (* 1000 (radians 180)))))
(define (a7) (let ((c (color 255 128 0))) (list (round (red c)) (round (green c)) (round (blue c)) (round (alpha c)))))
(define (a8) (round (red 100)))
(define (a9) (let ((c (lerp-color (color 0 0 0) (color 255 255 255) 0.5))) (round (red c))))
(define (a10) (>= (millis) 0))
;; py: a1() == [2, 4, 6]
;; py: a2() == 50
;; py: a3() == [11, 22]
;; py: a4() == [5.0, 10, 0, 0.5]
;; py: a5() == [5.0, 5.0, 3.0]
;; py: a6() == [180, 3142]
;; py: a7() == [255, 128, 0, 255]
;; py: a8() == 100
;; py: a9() == 128
;; py: a10() is True
