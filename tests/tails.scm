(define (tl1) (cdr (list 1 2 3)))
(define (tl2) (length (cdr (list 1 2 3))))
(define (tl3) (reverse (cdr (list 1 2 3))))
(define (tl4) (append (cdr (list 1 2)) (cdr (list 3 4))))
(define (tl5) (map (lambda (x) (* x 10)) (cdr (list 1 2 3))))
(define (tl6) (equal? (cdr (list 1 2 3)) (list 2 3)))
(define (tl7) (null? (cdr (list 1))))
(define (tl8) (eq? '() (cdr (list 1))))
(define (tl9) (apply + (cdr (list 1 2 3))))
(define (tl10) (cons 0 (cdr (list 1 2))))
(define (tl11) (list-tail (cdr (list 1 2 3 4)) 1))
(define (tl12) (pair? (cdr (list 1 2))))
(define (tl13) (cadr (cdr (list 1 2 3))))
(define (tl14) (let loop ((l (list 1 2 3 4)) (acc 0)) (if (null? l) acc (loop (cdr l) (+ acc (car l))))))
(define (tl15) (memv 3 (cdr (list 1 2 3 4))))
;; py: tl1() == [2, 3] and tl2() == 2 and tl3() == [3, 2]
;; py: tl4() == [2, 4] and tl5() == [20, 30] and tl6() is True
;; py: tl7() is True and tl8() is True and tl9() == 5 and tl10() == [0, 2]
;; py: tl11() == [3, 4] and tl12() is True and tl13() == 3 and tl14() == 10
;; py: tl15() == [3, 4]
;; py: _show(raw(tl1())) == '(2 3)'
(define (tl16) (cddr (list 1 2 3 4)))
(define (tl17) (caddr (list 1 2 3)))
(define (tl18) (cdar (list (list 1 2) 3)))
(define (tl19) (cddr (list 1)))
(define (tl20) (string-ref "abc" 5))
;; py: tl16() == [3, 4] and tl17() == 3 and tl18() == [2]
;; py: err_of(lambda: tl19()) == 'cdr: expected a pair, got ()'
;; py: err_of(lambda: tl20()) == 'string-ref: index 5 out of range for a string of length 3'
(define (tl21) (cadddr (list 1 2 3 4)))
(define (tl22) (fourth (list 1 2)))
;; py: tl21() == 4
;; py: err_of(lambda: tl22()) == 'cdr: expected a pair, got ()'
