# Values, text and lists

Every example on this page is checked by `scripts/doctest.py`.

## Number conversions

`int` truncates toward zero, like Java. `byte` wraps into -128 to 127. A string that isn't a number converts to 0.

```scheme
(int -3.7)        ;=> -3
(int "abc")       ;=> 0
(int #\A)         ;=> 65
(byte 128)        ;=> -128
(byte -129)       ;=> 127
(char 65)         ;=> #\A
(str 1.5)         ;=> "1.5"
(str (list #t #f)) ;=> ("true" "false")
```

## Booleans

Only the text `true` (in any case) is true. Any nonzero number is true.

```scheme
(boolean "TRUE")  ;=> #t
(boolean "yes")   ;=> #f
(boolean 0.0)     ;=> #f
(boolean -2)      ;=> #t
```

## Formatting numbers

```scheme
(nf 3.14159 1 2)  ;=> "3.14"
(nf -5 3)         ;=> "-005"
(nfc 1234567)     ;=> "1,234,567"
(nfp 5 2)         ;=> "+05"
(nfs 5 2)         ;=> " 05"
```

## Hex and binary

```scheme
(hex 255 2)       ;=> "FF"
(unhex "FF")      ;=> 255
(binary 5 4)      ;=> "0101"
(unbinary "101")  ;=> 5
```

## Strings

```scheme
(split "a,b,c" ",")       ;=> ("a" "b" "c")
(split-tokens "a b  c")   ;=> ("a" "b" "c")
(trim "  hi ")            ;=> "hi"
(join (list "a" "b") "-") ;=> "a-b"
```

`match` gives the matched groups, or `#f` when nothing matches.

```scheme
(match "abc123" "[0-9]+")  ;=> ("123")
(match "abc" "[0-9]")      ;=> #f
```

## Arrays at their limits

`expand` doubles an array, and pads with 0 for numbers or "" for strings. An empty array grows to one element.

```scheme
(expand (list "a"))       ;=> ("a" "")
(expand (list 1 2) 3)     ;=> (1 2 0)
(expand (list))           ;=> (0)
(shorten (list 1))        ;=> ()
(splice (list 1 2) 9 0)   ;=> (9 1 2)
(splice (list 1 2) 9 2)   ;=> (1 2 9)
(subset (list 1 2 3 4) 1 2) ;=> (2 3)
(subset (list 1 2 3) 1)   ;=> (2 3)
(concat (list 1) (list 2 3)) ;=> (1 2 3)
```

## Scheme's own list functions still work

Processing and Scheme both have functions named `map`, `filter` and `append`. Scheme's versions work on lists as usual.

```scheme
(map abs (list -1 2))             ;=> (1 2)
(filter even? (list 1 2 3 4))     ;=> (2 4)
(append (list 1) (list 2 3))      ;=> (1 2 3)
```

Processing's `map`, for numbers, rescales a value from one range to another.

```scheme
(map 5 0 10 0 100)    ;=> 50.0
```

## Math

```scheme
(sq 3)             ;=> 9
(norm 5 0 10)      ;=> 0.5
(mag 3 4)          ;=> 5.0
(degrees PI)       ;=> 180.0
(min 3 1 2)        ;=> 1
(constrain -5 0 10) ;=> 0
(constrain 5.5 0 10) ;=> 5.5
```

`round` follows Scheme here, not Processing: halves round to the even neighbor.

```scheme
(round 2.5)        ;=> 2.0
(round 3.5)        ;=> 4.0
```
