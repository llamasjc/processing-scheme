(define (bad-car) (car '()))
(define (thrower) (raise 'boom))
(define (multi x)
  (display x)
  (vector-ref (vector 1 2) x))
;; py: line_of(lambda: bad_car()) == 1
;; py: line_of(lambda: thrower()) == 2
;; py: line_of(lambda: multi(9)) == 5
