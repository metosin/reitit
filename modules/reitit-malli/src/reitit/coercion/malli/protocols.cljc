(ns reitit.coercion.malli.protocols)

(defprotocol Coercer
  (-decode [this value])
  (-encode [this value])
  (-validate [this value])
  (-explain [this value]))

(defprotocol TransformationProvider
  (-transformer [this options]))
