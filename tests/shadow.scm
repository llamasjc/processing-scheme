(define (sh1 x) (let ((x (* x 2))) (display x)) x)
(define (sh2 x) (let ((y 1)) (let ((y (+ y 1))) (set! x (+ x y))) x))
(define (sh3) (let* ((a 1) (a (+ a 1))) a))
(define (sh4 a b) (let ((a b) (b a)) (list a b)))
(define (sh5 n) (let ((acc 0)) (for-each (lambda (i) (set! acc (+ acc i))) (list 1 2 n)) acc))
(define w 5)
(define (sh6) (display w) (let ((w 1)) (+ w 1)))
(define (sh7) (let ((x 'outer)) (list x 'x)))
(define (sh8) (let ((v 3)) `(v ,v)))
(define (sh9 n) (let ((i 10)) (do ((i 0 (+ i 1)) (s 0 (+ s i))) ((= i n) (+ s i)))))
;; py: sh1(5) == 5
;; py: sh2(10) == 12
;; py: sh3() == 2
;; py: sh4(1, 2) == [2, 1]
;; py: sh5(3) == 6
;; py: sh6() == 2
;; py: sh7() == ['outer', 'x']
;; py: sh8() == ['v', 3]
;; py: sh9(3) == 6
