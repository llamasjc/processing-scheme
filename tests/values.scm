; Built-ins used as values, and vectors through the object-first form.
(define (v1) (map abs (list -1 2 -3)))
(define (v2) (map quotient (list 7 9) (list 2 4)))
(define (v3) (filter odd? (iota 6)))
(define (v4) (map not (list #t #f)))
(define (v5) (map string-upcase (list "a" "b")))
(define (v6) (assoc 2 (list (list 1 "a") (list 2 "b"))))
(define (v7) (map list-ref (list (list 1 2) (list 3 4)) (list 1 0)))
(define (v8) (apply + (list 1 2 3)))
(define (w1) (mag (PVector 3 4)))
(define (w2) (heading (PVector 0 1)))
(define (w3) (let ((a (PVector 1 2)) (b (PVector 3 4))) (add a b) (list (vec-x a) (vec-y a))))
(define (w4) (max 3 7 5))
;; py: v1() == [1, 2, 3]
;; py: v2() == [3, 2]
;; py: v3() == [1, 3, 5]
;; py: v4() == [False, True]
;; py: v5() == ['A', 'B']
;; py: v6() == [2, 'b']
;; py: v7() == [2, 3]
;; py: v8() == 6
;; py: abs(w1() - 5.0) < 1e-6
;; py: abs(w2() - 1.5707963) < 1e-5
;; py: w3() == [4.0, 6.0]
;; py: w4() == 7
