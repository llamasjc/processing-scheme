(define x 0)
(define (b1) (begin 1 2))
(define (b2) (+ 10 (begin (set! x 5) x)))
(define (b3) (if (> x 0) (begin (print "pos") x) 'neg))
(define (n1) (string->number "42"))
(define (n2) (number->string 255 16))
;; py: b1() == 2
;; py: b2() == 15
;; py: n1() == 42 and n2() == 'ff'
