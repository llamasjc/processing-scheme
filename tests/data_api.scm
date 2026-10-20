; Tables, JSON, XML and array helpers.
(define (t1)
  (let ((t (create-table)))
    (add-column t "name")
    (add-column t "age")
    (let ((r (add-row t)))
      (set-string r "name" "ada")
      (set-int r "age" 36))
    (list (get-row-count t) (get-column-count t) (get-string t 0 "name") (get-int t 0 "age"))))
(define (t2)
  (let ((d (parse-json-object "{\"a\": 1, \"b\": [2, 3]}")))
    (list (has-key d "a") (get-int d "a") (get-int (get-json-array d "b") 1) (keys d) (get-int d "zz" 7))))
(define (t3)
  (list (concat (list 1 2) (list 3)) (subset (list 1 2 3 4) 1 2) (sort-reverse (list 1 3 2)) (length (shuffle (list 1 2 3)))))
(define (t4)
  (let ((x (parse-xml "<a><b k=\"1\">hi</b><b k=\"2\"/></a>")))
    (list (get-name x) (get-child-count x) (get-content (get-child x 0)) (get-int (get-child x 1) "k")
          (has-attribute (get-child x 0) "k") (length (get-children x "b")))))
(define (t5) (let ((d (make-hash-table))) (increment d "x") (increment d "x" 2) (get-int d "x")))
;; py: t1() == [1, 2, 'ada', 36]
;; py: t2() == [True, 1, 3, ['a', 'b'], 7]
;; py: t3() == [[1, 2, 3], [2, 3], [3, 2, 1], 3]
;; py: t4() == ['a', 2, 'hi', 2, True, 2]
;; py: t5() == 3
