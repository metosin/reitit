(ns user
  (:require
   [integrant.repl :as ig-repl]
   [example.server]))

(ig-repl/set-prep! (constantly example.server/system-config))

(def go ig-repl/go)
(def halt ig-repl/halt)
(def reset ig-repl/reset)

(comment
  (go)
  (reset)
  (halt))
