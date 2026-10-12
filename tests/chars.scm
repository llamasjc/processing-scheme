(define (c1) (char? #\a))
(define (c2) (char? "a"))
(define (c3) (string? #\a))
(define (c4) (string-ref "abc" 1))
(define (c5) (char? (string-ref "abc" 1)))
(define (c6) (list->string (string->list "hey")))
(define (c7) (char-upcase #\a))
(define (c8) (char? (char-upcase #\a)))
(define (c9) (char->integer #\A))
(define (c10) #\a)
(define (c11) (char=? #\a (string-ref "a" 0)))
(define (c12) (case #\b ((#\a) 1) ((#\b) 2) (else 3)))
(define (c13) (list #\x #\space))
;; py: c1() is True and c2() is False and c3() is False
;; py: c4() == 'b' and c5() is True
;; py: c6() == 'hey'
;; py: c7() == 'A' and c8() is True
;; py: c9() == 65
;; py: _show(raw(c10()), True) == '#' + chr(92) + 'a'
;; py: c11() is True
;; py: c12() == 2
;; py: _show(raw(c13()), True) == '(#' + chr(92) + 'x #' + chr(92) + 'space)'
