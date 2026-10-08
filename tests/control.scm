(define (c1 x) (cond ((assv x (list (list 1 'one) (list 2 'two))) => cadr) (else 'none)))
(define (c2 x) (case x ((1 2 3) 'small) ((4 5 6) 'mid) (else 'big)))
(define (c3 n) (do ((i 0 (+ i 1)) (acc 0 (+ acc i))) ((= i n) acc)))
(define (c4 x) (guard (e (#t (list 'caught e))) (if (< x 0) (raise 'neg) (* x 2))))
(define (c5 x) (when (> x 0) 'pos))
(define (c6) (apply + 1 2 (list 3 4)))
(define (c7) (let loop ((i 0) (acc (list))) (if (= i 3) (reverse acc) (loop (+ i 1) (cons (* i i) acc)))))
(define (c8 x) (cond ((> x 10) 'big) ((> x 5)) (else 'small)))
;; py: c1(2) == 'two' and c1(9) == 'none'
;; py: c2(2) == 'small' and c2(5) == 'mid' and c2(9) == 'big'
;; py: c3(4) == 6
;; py: c4(3) == 6 and c4(-1) == ['caught', 'neg']
;; py: c5(1) == 'pos'
;; py: c6() == 10
;; py: c7() == [0, 1, 4]
;; py: c8(20) == 'big' and c8(7) == True and c8(1) == 'small'
(define (c9 x) (+ 1 (cond ((> x 0) (display "p") 10) (else 20))))
(define (c10 x) (cond ((assv x (list (list 1 'a))) => (lambda (p) (display (cadr p))))) 'done)
;; py: c9(1) == 11 and c9(-1) == 21
;; py: c10(1) == 'done' and c10(5) == 'done'
