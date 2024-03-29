(ns reitit.ring.malli
  #?(:clj (:import (java.io File))))

#?(:clj
   (def temp-file-part
     "Schema for file param created by ring.middleware.multipart-params.temp-file store."
     [:map {:swagger {:type "file"}
            :json-schema {:type "string"
                          :format "binary"}}
      [:filename string?]
      [:content-type string?]
      [:size int?]
      [:tempfile [:fn (partial instance? File)]]]))

#?(:clj
   (def bytes-part
     "Schema for file param created by ring.middleware.multipart-params.byte-array store."
     [:map {:swagger {:type "file"}
            :json-schema {:type "string"
                          :format "binary"}}
      [:filename string?]
      [:content-type string?]
      [:bytes bytes?]]))
