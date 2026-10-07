(define total 0)
(define (g1) (for-each (lambda (x) (set! total (+ total x))) (list 1 2 3)) total)
(define (g2) (let ((n 0)) (for-each (lambda (x) (set! n (+ n 1)) (print x)) (list 7 8)) n))
(define (g3) (let ((acc (list))) (for-each (lambda (a b) (set! acc (cons (+ a b) acc))) (list 1 2) (list 10 20)) acc))
