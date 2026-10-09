(define xs '())
(define (n1) (set! xs (let loop ((i 0) (acc '())) (if (= i 3) (reverse acc) (loop (+ i 1) (cons i acc))))) xs)
(define (n2) (+ 1 (let loop ((i 0)) (if (< i 5) (loop (+ i 1)) i))))
(define (n3 x) (when (> x 0) (display "p") (let loop ((i x) (s 0)) (if (= i 0) s (loop (- i 1) (+ s i))))))
(define (n4) (list (let loop ((i 0)) (if (= i 2) 'a (loop (+ i 1)))) (let loop ((i 0)) (if (= i 3) 'b (loop (+ i 1))))))
(define ys (let loop ((i 0) (acc '())) (if (= i 2) acc (loop (+ i 1) (cons i acc)))))
;; py: n1() == [0, 1, 2]
;; py: n2() == 6
;; py: n3(3) == 6
;; py: n4() == ['a', 'b']
;; py: ys == [1, 0]
(define (n5) (map (lambda (n) (let loop ((i n) (s 0)) (if (= i 0) s (loop (- i 1) (+ s i))))) (list 1 2 3)))
;; py: n5() == [1, 3, 6]
