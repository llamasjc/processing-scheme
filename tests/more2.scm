(define (q2) (fold + 0 (list 1 2 3)))
(define (q3) (filter odd? (list 1 2 3 4 5)))
(define (q4) (reduce + 0 (list 1 2 3)))
(define (q5) (iota 5))
(define (q6) (list-sort < (list 3 1 2)))
(define (q6b) (sort (list 3 1 2) <))
(define (q7) (string-map char-upcase "abc"))
(define (q8) (let ((n 0)) (string-for-each (lambda (c) (set! n (+ n 1))) "abcd") n))
(define (q9) (guard (e ((error-object? e) (error-object-message e))) (error "bad thing" 42)))
(define (q9b) (guard (e ((error-object? e) (error-object-irritants e))) (error "bad thing" 42)))
(define (q10) (call/cc (lambda (k) (+ 1 (k 42)))))
(define (q11) (with-exception-handler (lambda (e) 10) (lambda () (+ 1 (raise-continuable 'oops)))))
(define (q12) (square 5))
(define (q13) (exact-integer? 5))
(define (q15) (assq 'c (list (list 'a 1) (list 'b 2))))
(define (q17) (string-copy "abc" 1))
(define (q19) (number->string 2.5))
(define (q23) (dynamic-wind (lambda () #f) (lambda () 5) (lambda () #f)))
(define (q24) (digit-value #\7))
(define (q26) (list-tail (list 1 2 3) 3))
(define (q27) (boolean? '()))
(define (q28) (exact (round 2.5)))
(define (q29) (exact (round 3.5)))
;; py: q2() == 6
;; py: q3() == [1, 3, 5]
;; py: q4() == 6
;; py: q5() == [0, 1, 2, 3, 4]
;; py: q6() == [1, 2, 3]
;; py: q6b() == [1, 2, 3]
;; py: q7() == 'ABC'
;; py: q8() == 4
;; py: q9() == 'bad thing'
;; py: q9b() == [42]
;; py: q10() == 42
;; py: q11() == 11
;; py: q12() == 25
;; py: q13() is True
;; py: q15() is False
;; py: q17() == 'bc'
;; py: q19() == '2.5'
;; py: q23() == 5
;; py: q24() == 7
;; py: q26() == []
;; py: q27() is False
;; py: q28() == 2
;; py: q29() == 4
