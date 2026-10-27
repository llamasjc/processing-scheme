; Processing conversions and array helpers.
(define (k1) (list (int 3.7) (int "42") (int #\A) (int #t)))
(define (k2) (list (float "2.5") (float 3)))
(define (k3) (list (boolean "true") (boolean 0) (boolean 5)))
(define (k4) (list (byte 200) (byte -1) (byte 127)))
(define (k5) (list (str #t) (str 42) (str (list 1 2))))
(define (k6) (char 65))
(define (k7) (list (expand (list 1 2)) (shorten (list 1 2 3)) (splice (list 1 4) (list 2 3) 1)))
(define (k8) (let ((dst (make-vector 4 0))) (array-copy (list 9 8 7) 0 dst 1 2) dst))
(define (k9) (int (list 1.5 2.9)))
;; py: k1() == [3, 42, 65, 1]
;; py: k2() == [2.5, 3.0]
;; py: k3() == [True, False, True]
;; py: k4() == [-56, -1, 127]
;; py: k5() == ['true', '42', ['1', '2']]
;; py: k6() == 'A'
;; py: k7() == [[1, 2, 0, 0], [1, 2], [1, 2, 3, 4]]
;; py: k8() == [0, 9, 8, 0]
;; py: k9() == [1, 2]
