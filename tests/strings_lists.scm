(define (t1) (list (string-ref "abc" 1) (substring "hello" 1 3) (string-upcase "ab") (string->symbol "x")
                   (char->integer #\A) (integer->char 66) (string-index "abc" #\b)))
(define (t2) (list (map + (list 1 2) (list 10 20)) (apply max (list 3 9 2)) (reverse (list 1 2 3))
                   (append (list 1) (list 2 3) (list)) (length (list 1 2)) (list-copy (list 1 2))))
(define (t3) (list (assq 'b (list (list 'a 1) (list 'b 2))) (assv 2 (list (list 1 'x) (list 2 'y)))
                   (null? (list)) (pair? (list)) (list? (list 1)) (boolean=? #t #t)))
(define (t4) (let ((v (make-vector 3 0))) (vector-set! v 0 9) (vector-fill! v 7) (list (vector-ref v 1) (vector-length v) (vector->list v))))
(define (t5) (let ((acc (list))) (for-each (lambda (a b) (set! acc (cons (+ a b) acc))) (list 1 2) (list 10 20)) acc))
;; py: t1() == ['b', 'el', 'AB', 'x', 65, 'B', 1]
;; py: t2() == [[11, 22], 9, [3, 2, 1], [1, 2, 3], 2, [1, 2]]
;; py: t3() == [['b', 2], [2, 'y'], True, False, True, True]
;; py: t4() == [7, 3, [7, 7, 7]]
;; py: t5() == [22, 11]
