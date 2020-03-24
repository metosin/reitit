(ns reitit.ring.spec
  (:require [spec-tools.core :as st]
            [clojure.spec.alpha :as s])
  (:import (java.io File)))

(s/def ::filename string?)
(s/def ::content-type string?)
(s/def ::tempfile (partial instance? File))
(s/def ::bytes bytes?)
(s/def ::size int?)

(def temp-file-part
  "Spec for file param created by ring.middleware.multipart-params.temp-file store."
  (st/spec
    {:spec (s/keys :req-un [::filename ::content-type ::tempfile ::size])
     :swagger/type "file"}))

(def bytes-part
  "Spec for file param created by ring.middleware.multipart-params.byte-array store."
  (st/spec
    {:spec (s/keys :req-un [::filename ::content-type ::bytes])
     :swagger/type "file"}))

