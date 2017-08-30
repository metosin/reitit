(ns reitit.coercion.protocol
  (:refer-clojure :exclude [compile]))

(defprotocol Coercion
  (get-name [this])
  (compile [this model])
  (get-apidocs [this model data])
  (make-open [this model])
  (encode-error [this error])
  (request-coercer [this type model])
  (response-coercer [this model]))

(defrecord CoercionError [])

(defn error? [x]
  (instance? CoercionError x))
