(define (count-up n) (let loop ((i 0)) (if (< i n) (loop (+ i 1)) i)))
(define (len l) (if (null? l) 0 (+ 1 (len (cdr l)))))
(define (sum-acc l) (let loop ((l l) (acc 0)) (if (null? l) acc (loop (cdr l) (+ acc (car l))))))
;; py: count_up(1000) == 1000
;; py: count_up(100000) == 100000
;; py: sum_acc(list(range(100000))) == 4999950000
;; py: _len(list(range(5000))) == 5000
(define (t1) (let loop ((i 0) (acc '())) (cond ((= i 3) (reverse acc)) (else (loop (+ i 1) (cons i acc))))))
(define (t2 n) (let loop ((i n)) (begin (if (= i 0) 'done (loop (- i 1))))))
(define (t3) (let loop ((i 0)) (let ((j (+ i 1))) (if (< j 100000) (loop j) j))))
(define (t4) (let loop ((i 0) (s 0)) (when (< i 5) (set! s (+ s i))) (if (< i 5) (loop (+ i 1) s) s)))
(define (t5) (let loop ((a 1) (b 2) (n 0)) (if (= n 1) (list a b) (loop b a (+ n 1)))))
(define (t6) (let loop ((i 0)) (if (< i 3) (begin (display i) (loop (+ i 1))) 'end)))
;; py: t1() == [0, 1, 2]
;; py: t2(100000) == 'done'
;; py: t3() == 100000
;; py: t4() == 10
;; py: t5() == [2, 1]
;; py: t6() == 'end'
