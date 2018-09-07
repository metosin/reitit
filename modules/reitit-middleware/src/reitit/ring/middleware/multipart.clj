(ns reitit.ring.middleware.multipart
  (:refer-clojure :exclude [compile])
  (:require [reitit.coercion :as coercion]
            [ring.middleware.multipart-params :as multipart-params]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st])
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

(defn- coerced-request [request coercers]
  (if-let [coerced (if coercers (coercion/coerce-request coercers request))]
    (update request :parameters merge coerced)
    request))

(defn- compile [options]
  (fn [{:keys [parameters coercion]} opts]
    (if-let [multipart (:multipart parameters)]
      (let [parameter-coercion {:multipart (coercion/->ParameterCoercion
                                             :multipart-params :string true true)}
            opts (assoc opts ::coercion/parameter-coercion parameter-coercion)
            coercers (if multipart (coercion/request-coercers coercion parameters opts))]
        {:data {:swagger {:consumes ^:replace #{"multipart/form-data"}}}
         :wrap (fn [handler]
                 (fn
                   ([request]
                    (-> request
                        (multipart-params/multipart-params-request options)
                        (coerced-request coercers)
                        (handler)))
                   ([request respond raise]
                    (-> request
                        (multipart-params/multipart-params-request options)
                        (coerced-request coercers)
                        (handler respond raise)))))}))))

;;
;; public api
;;

(defn create-multipart-middleware
  "Creates a Middleware to handle the multipart params, based on
  ring.middleware.multipart-params, taking same options. Mounts only
  if endpoint has `[:parameters :multipart]` defined. Publishes coerced
  parameters into `[:parameters :multipart]` under request."
  ([]
   (create-multipart-middleware nil))
  ([options]
   {:name ::multipart
    :compile (compile options)}))

(def multipart-middleware
  "Middleware to handle the multipart params, based on
  ring.middleware.multipart-params, taking same options. Mounts only
  if endpoint has `[:parameters :multipart]` defined. Publishes coerced
  parameters into `[:parameters :multipart]` under request."
  (create-multipart-middleware))
