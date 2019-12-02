(ns reitit.impl-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.impl :as impl]))

(deftest strip-nils-test
  (is (= {:a 1, :c false} (impl/strip-nils {:a 1, :b nil, :c false}))))

(deftest url-encode-and-decode-test
  (is (= "reitit.impl-test%2Fkikka" (-> ::kikka
                                        impl/into-string
                                        impl/url-encode)))
  (is (= ::kikka (-> ::kikka
                     impl/into-string
                     impl/url-encode
                     impl/url-decode
                     keyword))))

(deftest path-params-test
  (is (= {:n "1"
          :n1 "-1"
          :n2 "1"
          :n3 "1"
          :n4 "1"
          :n5 "1"
          :d "2.2"
          :b "true"
          :s "kikka"
          :u "c2541900-17a7-4353-9024-db8ac258ba4e"
          :k "kikka"
          :qk "reitit.impl-test%2Fkikka"
          :nil nil}
         (impl/path-params {:n 1
                            :n1 -1
                            :n2 (long 1)
                            :n3 (int 1)
                            :n4 (short 1)
                            :n5 (byte 1)
                            :d 2.2
                            :b true
                            :s "kikka"
                            :u #uuid "c2541900-17a7-4353-9024-db8ac258ba4e"
                            :k :kikka
                            :qk ::kikka
                            :nil nil}))))

(deftest query-params-test
  (are [x y]
    (= (impl/query-string x) y)
    {:a "b"} "a=b"
    {"a" "b"} "a=b"
    {:a 1} "a=1"
    {:a nil} "a="
    {:a :b :c "d"} "a=b&c=d"
    {:a "b c"} "a=b+c"
    {:a ["b" "c"]} "a=b&a=c"
    {:a ["c" "b"]} "a=c&a=b"
    {:a (seq [1 2])} "a=1&a=2"
    {:a #{"c" "b"}} "a=b&a=c"))

;; test from https://github.com/playframework/playframework -> UriEncodingSpec.scala

(deftest url-encode-test
  (are [in out]
    (= out (impl/url-encode in))

    "/" "%2F"
    "?" "%3F"
    "#" "%23"
    "[" "%5B"
    "]" "%5D"
    "!" "!"
    #_#_"$" "$"
    #_#_"&" "&"
    "'" "'"
    "(" "("
    ")" ")"
    "*" "*"
    #_#_"+" "+"
    #_#_"," ","
    #_#_";" ";"
    #_#_"=" "="
    #_#_":" ":"
    #_#_"@" "@"
    "a" "a"
    "z" "z"
    "A" "A"
    "Z" "Z"
    "0" "0"
    "9" "9"
    "-" "-"
    "." "."
    "_" "_"
    "~" "~"
    "\000" "%00"
    "\037" "%1F"
    " " "%20"
    "\"" "%22"
    "%" "%25"
    "<" "%3C"
    ">" "%3E"
    "\\" "%5C"
    "^" "%5E"
    "`" "%60"
    "{" "%7B"
    "|" "%7C"
    "}" "%7D"
    "\177" "%7F"
    #_#_"\377" "%FF"

    "£0.25" "%C2%A30.25"
    "€100" "%E2%82%AC100"
    "«küßî»" "%C2%ABk%C3%BC%C3%9F%C3%AE%C2%BB"
    "“ЌύБЇ”" "%E2%80%9C%D0%8C%CF%8D%D0%91%D0%87%E2%80%9D"

    "\000" "%00"
    #_#_"\231" "%99"
    #_#_"\252" "%AA"
    #_#_"\377" "%FF"

    "" ""
    "1" "1"
    "12" "12"
    "123" "123"
    "1234567890" "1234567890"

    "Hello world" "Hello%20world"
    "/home/foo" "%2Fhome%2Ffoo"

    " " "%20"
    "+" "%2B" #_"+"
    " +" "%20%2B" #_"%20+"
    #_#_"1+2=3" "1+2=3"
    #_#_"1 + 2 = 3" "1%20+%202%20=%203"))

(deftest url-decode-test
  (are [in out]
    (= out (impl/url-decode in))

    "1+1" "1+1"
    "%21" "!"
    "%61" "a"
    "%31%32%33" "123"
    "%2b" "+"
    "%7e" "~"
    "hello%20world" "hello world"
    "a%2fb" "a/b"
    "a/.." "a/.."
    "a/." "a/."
    "//a" "//a"
    "a//b" "a//b"
    "a//" "a//"
    "/path/%C2%ABk%C3%BC%C3%9F%C3%AE%C2%BB" "/path/«küßî»"
    "/path/%E2%80%9C%D0%8C%CF%8D%D0%91%D0%87%E2%80%9D" "/path/“ЌύБЇ”"))

(deftest form-encode-test
  (are [in out]
    (= out (impl/form-encode in))

    "+632 905 123 4567" "%2B632+905+123+4567"))

(deftest form-decode-test
  (are [in out]
    (= out (impl/form-decode in))

    "%2B632+905+123+4567" "+632 905 123 4567"))

(deftest parse-test
  (is (= {:path "https://google.com"
          :path-parts ["https://google.com"]
          :path-params #{}}
         (impl/parse "https://google.com" nil))))
