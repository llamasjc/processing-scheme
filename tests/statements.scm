; Statements where Scheme allows them: loops and defines inside a cond in
; tail position, and inside a for-each lambda.
(define (t1 axis)
  (define acc 0)
  (cond
    ((= axis 1)
     (do ((i 0 (+ i 1))) ((= i 3))
       (define k (* i 2))
       (set! acc (+ acc k)))
     acc)
    (else -1)))
;; py: t1(1) == 6
;; py: t1(0) == -1
(define (t2 xs)
  (define n 0)
  (for-each (lambda (x)
              (define d (* x 2))
              (when (> d 2) (set! n (+ n 1))))
            xs)
  n)
;; py: t2([1, 2, 3]) == 2
(define (t3 i)
  (let ((xs (list 1 2 3)))
    (for-each (lambda (i) (set! xs (cons i xs))) (list 9))
    (list i (length xs))))
;; py: t3(5) == [5, 4]
(define (t4) (second (list 1 2 3)))
;; py: t4() == 2
