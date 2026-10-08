(define (q) (list (quotient -7 2) (remainder -7 2) (modulo -7 2)))
(define (r) (list (round 2.5) (truncate -2.7) (floor -2.7) (exact (floor 2.7)) (exact->inexact 1)))
(define (e) (list (expt 2 10) (expt 2.0 0.5) (abs -3) (even? 4) (exact? 1) (inexact? 1.0)))
(define (eqs) (list (eq? 'a 'a) (eqv? 1.0 1.0) (equal? (list 1 2) (list 1 2)) (= 1 1.0)))
(define (lists) (list (assoc 2 (list (list 1 'a) (list 2 'b))) (member 3 (list 1 2)) (list-tail (list 1 2 3) 1)))
;; py: q() == [-3, -1, 1]
;; py: eqs() == [True, True, True, True]
;; py: lists() == [[2, 'b'], False, [2, 3]]
;; py: e() == [1024, 2.0 ** 0.5, 3, True, True, True]
