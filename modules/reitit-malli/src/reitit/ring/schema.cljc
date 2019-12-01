(ns reitit.ring.malli)

(comment

  (defrecord Upload [m]
             s/Schema
             (spec [_]
                   (s/spec m))
             (explain [_]
                      (list 'file m))

             swagger/SwaggerSchema
             (-transform [_ _]
                         {:type "file"}))

  #?(:clj
     (def TempFilePart
       "Schema for file param created by ring.middleware.multipart-params.temp-file store."
       (->Upload {:filename s/Str
                  :content-type s/Str
                  :size s/Int
                  :tempfile File})))

  #?(:clj
     (def BytesPart
       "Schema for file param created by ring.middleware.multipart-params.byte-array store."
       (->Upload {:filename s/Str
                  :content-type s/Str
                  :bytes s/Any}))))
