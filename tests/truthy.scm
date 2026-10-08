(define (k1 x) (if x 'yes 'no))
(define (k2 x) (cond (x 'yes) (else 'no)))
(define (k3 x) (when x 'yes))
(define (k4 x) (not x))
(define (k5 a b) (and a b))
(define (k6 a b) (or a b))
(define (k7 n) (let loop ((i n) (acc (list))) (if (null? i) acc (loop '() (cons 'x acc)))))
(define (k8 x) (if (> x 0) 'pos 'nonpos))
;; py: k1(0) == 'yes' and k1([]) == 'yes' and k1('') == 'yes' and k1(False) == 'no'
;; py: k2(0) == 'yes' and k2(False) == 'no'
;; py: k3(0) == 'yes'
;; py: k4(0) is False and k4(False) is True and k4([]) is False
;; py: k5(0, 5) == 5 and k5(False, 5) is False and k5(1, 0) == 0
;; py: k6(0, 5) == 0 and k6(False, 5) == 5 and k6(False, False) is False
;; py: k7(3) == ['x']
;; py: k8(3) == 'pos' and k8(0) == 'nonpos'
