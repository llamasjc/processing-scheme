(define (p1) (let ((p (open-output-string))) (display "a" p) (write "b" p) (newline p) (get-output-string p)))
(define (p2) (with-output-to-string (lambda () (display 42))))
(define (p3) (let ((p (open-input-string "hi
yo"))) (list (read-char p) (peek-char p) (read-line p) (read-line p) (eof-object? (read-char p)))))
(define ht (make-hash-table))
(define (h1) (hash-table-set! ht 'a 1) (hash-table-set! ht 'b 2) (hash-table-ref/default ht 'a 0))
(define (h2) (hash-table-ref/default ht 'zz 'none))
(define (h3) (hash-table-update!/default ht 'a (lambda (v) (+ v 10)) 0) (hash-table-ref ht 'a))
(define (h4) (hash-table-count ht))
(define (h5) (hash-table-contains? ht 'b))
(define-values (dv1 dv2) (values 7 8))
(define (d1) (define-values (x y) (values 1 2)) (+ x y))
;; py: p1() == 'a"b"' + chr(10)
;; py: p2() == '42'
;; py: p3() == ['h', 'i', 'i', 'yo', True]
;; py: h1() == 1
;; py: h2() == 'none'
;; py: h3() == 11
;; py: h4() == 2
;; py: h5() is True
;; py: dv1 == 7 and dv2 == 8
;; py: d1() == 3
;; py: all(0 <= noise(i * 0.37) <= 1 for i in range(50))
;; py: abs(noise(1.0) - noise(1.001)) < 0.01
;; py: len(set(round(noise(i), 3) for i in range(10))) > 5
;; py: noise(0.5, 0.25) != noise(0.25, 0.5)
