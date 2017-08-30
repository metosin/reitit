(ns reitit.coercion.protocol
  (:refer-clojure :exclude [compile]))

(defprotocol Coercion
  "Pluggable coercion protocol"
  (get-name [this] "Keyword name for the coercion")
  (compile [this model name] "Compiles a coercion model")
  (get-apidocs [this model data] "???")
  (make-open [this model] "Returns a new map model which doesn't fail on extra keys")
  (encode-error [this error] "Converts error in to a serializable format")
  (request-coercer [this type model] "Returns a `value format => value` request coercion function")
  (response-coercer [this model] "Returns a `value format => value` response coercion function"))

(defrecord CoercionError [])

(defn error? [x]
  (instance? CoercionError x))
