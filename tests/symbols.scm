(define (s1) (symbol? 'a))
(define (s2) (symbol? "a"))
(define (s3) (string? 'a))
(define (s4) (eq? 'a (string->symbol "a")))
(define (s5) (equal? 'a "a"))
(define (s6) (string? (symbol->string 'abc)))
(define (s7) (case 'b ((a) 1) ((b c) 2) (else 3)))
(define (s8) (assq 'y (list (list 'x 1) (list 'y 2))))
(define (s9) (memq 'c '(a b c d)))
(define (s10) (list 'a "b"))
(define (s11) (eq? 'q 'q))
;; py: s1() is True and s2() is False and s3() is False
;; py: s4() is True and s5() is False and s6() is True
;; py: s7() == 2 and s8() == ['y', 2] and s9() == ['c', 'd']
;; py: _show(raw(s10()), True) == '(a "b")'
;; py: s11() is True
