(ns reitit.exception)

(defn fail!
  ([message]
    (throw (ex-info message {::type :exeption})))
  ([message data]
   (throw (ex-info message (merge {::type ::exeption} data)))))
