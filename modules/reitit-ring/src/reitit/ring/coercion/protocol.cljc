(ns reitit.ring.coercion.protocol)

(defprotocol Coercion
  "Pluggable coercion protocol"
  (get-name [this] "Keyword name for the coercion")
  (get-apidocs [this model data] "???")
  (compile-model [this model name] "Compiles a model")
  (open-model [this model] "Returns a new model which allows extra keys in maps")
  (encode-error [this error] "Converts error in to a serializable format")
  (request-coercer [this type model] "Returns a `value format => value` request coercion function")
  (response-coercer [this model] "Returns a `value format => value` response coercion function"))

(defrecord CoercionError [])

(defn error? [x]
  (instance? CoercionError x))
