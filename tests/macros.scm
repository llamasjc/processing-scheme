(define-syntax head-and-tail
  (syntax-rules () ((_ a . rest) (begin (print a) (list . rest)))))
(define-syntax swap!
  (syntax-rules () ((_ a b) (let ((tmp a)) (set! a b) (set! b tmp)))))
(define-syntax my-or
  (syntax-rules () ((_) #f) ((_ e) e) ((_ e r ...) (let ((t e)) (if t t (my-or r ...))))))
(define-syntax my-let
  (syntax-rules () ((_ ((n v) ...) body ...) ((lambda (n ...) body ...) v ...))))
(define (demo) (head-and-tail 1 2 3))
(define (demo2) (head-and-tail 1))
(define (h1 tmp other) (swap! tmp other) (list tmp other))
(define (h2 t) (my-or #f t))
(define (h3) (my-let ((p 1) (q 2)) (+ p q)))
