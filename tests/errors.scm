(define (bad-car) (car '()))
(define (thrower) (raise 'boom))
(define (multi x)
  (display x)
  (vector-ref (vector 1 2) x))
;; py: line_of(lambda: bad_car()) == 1
;; py: line_of(lambda: thrower()) == 2
;; py: line_of(lambda: multi(9)) == 5
(define (bad-cdr) (cdr '()))
(define (pair-car) (car (cons 1 2)))
;; py: line_of(lambda: bad_cdr()) == 9
;; py: err_of(lambda: bad_car()) == 'car: expected a pair, got ()'
;; py: err_of(lambda: bad_cdr()) == 'cdr: expected a pair, got ()'
;; py: err_of(lambda: multi(9)) == 'vector-ref: index 9 out of range for a vector of length 2'
;; py: pair_car() == 1
