(define (make-counter) (let ((n 0)) (lambda () (set! n (+ n 1)) n)))
(define (m1) (let ((c (make-counter))) (c) (c) (c)))
(define (m2) (/ 6 3))
(define (sum . xs) (apply + xs))
(define (m4) (sum 1 2 3))
(define (m5) (sum))
(define (m6) (+ 1 2 3 4))
(define (m7) (*))
(define (m8 x) (define y (* x 2)) (define (h z) (+ y z)) (h 1))
(define (m9) (letrec ((ev? (lambda (n) (if (= n 0) #t (od? (- n 1)))))
                      (od? (lambda (n) (if (= n 0) #f (ev? (- n 1))))))
               (ev? 10)))
(define (m10) (let ((l (list 1 2 3))) (set-car! l 9) l))
(define (m11) (list->string (list #\a #\b)))
(define (m13) (char-upcase #\a))
(define (m16) (max 1 2.5))
(define (m18) (string-append))
(define (m19) (vector-map (lambda (x) (* x x)) (vector 1 2 3)))
(define (m20) (let ((acc 0)) (vector-for-each (lambda (x) (set! acc (+ acc x))) (vector 1 2 3)) acc))
(define (m21) (assoc "b" (list (list "a" 1) (list "b" 2))))
(define (m22) (let-values (((a b) (values 1 2))) (+ a b)))
(define (m23) `(1 ,@(list 2 3) 4))
(define (m24) (exact (floor 2.5)))
(define (m26) (expt 2 -1))
(define (m27) (string=? "a" "a" "a"))
(define (m28) (< 1 2 3))
(define (m29) (= 1 1 2))
(define (m30) (- 5))
(define (m32) (length '()))
(define (m33) (apply max 1 (list 5 3)))
;; py: m1() == 3
;; py: m2() == 2 and type(m2()) is int
;; py: m4() == 6
;; py: m5() == 0
;; py: m6() == 10
;; py: m7() == 1
;; py: m8(3) == 7
;; py: m9() is True
;; py: m10() == [9, 2, 3]
;; py: m11() == 'ab'
;; py: m13() == 'A'
;; py: m16() == 2.5
;; py: m18() == ''
;; py: m19() == [1, 4, 9]
;; py: m20() == 6
;; py: m21() == ['b', 2]
;; py: m22() == 3
;; py: m23() == [1, 2, 3, 4]
;; py: m24() == 2 and type(m24()) is int
;; py: m26() == 0.5
;; py: m27() is True
;; py: m28() is True
;; py: m29() is False
;; py: m30() == -5
;; py: m32() == 0
;; py: m33() == 5
(define (m34) (/ 7 2))
(define (m35) (/ 2))
(define (m36) (apply < (list 1 2 3)))
(define (m37) (apply = (list 1 1 2)))
(define (m38) (apply / (list 12 2 3)))
;; py: m34() == 3.5
;; py: m35() == 0.5
;; py: m36() is True and m37() is False
;; py: m38() == 2 and type(m38()) is int
