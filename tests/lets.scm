(define (l1) (let ((a 1)) (print a)) (print 2))
(define (l2 x) (let ((y (* x 2))) (+ y 1)))
(define (l3) (let* ((a 1) (b (+ a 1))) (list a b)))
(define (l4 x) (if (> x 0) (let ((y x)) (* y 10)) 0))
