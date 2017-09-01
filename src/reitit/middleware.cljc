(ns reitit.middleware
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.core :as reitit])
  #?(:clj
     (:import (clojure.lang IFn AFn))))

(defprotocol ExpandMiddleware
  (expand-middleware [this meta opts]))

(defrecord MiddlewareGenerator [f args]
  IFn
  (invoke [_]
    (f nil nil))
  (invoke [_ meta]
    (f meta nil))
  (invoke [_ meta opts]
    (f meta opts))
  #?(:clj
     (applyTo [this args]
       (AFn/applyToHelper this args))))

(extend-protocol ExpandMiddleware

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (expand-middleware [[f & args] meta opts]
    (if-let [mw (expand-middleware f meta opts)]
      (fn [handler]
        (apply mw handler args))))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (expand-middleware [this _ _] this)

  MiddlewareGenerator
  (expand-middleware [this meta opts]
    (if-let [mw (this meta opts)]
      (fn [handler & args]
        (apply mw handler args))))

  nil
  (expand-middleware [_ _ _]))

(defn- ensure-handler! [path meta scope]
  (when-not (:handler meta)
    (throw (ex-info
             (str "path \"" path "\" doesn't have a :handler defined"
                  (if scope (str " for " scope)))
             (merge {:path path, :meta meta}
                    (if scope {:scope scope}))))))

(defn compose-middleware [middleware meta opts]
  (->> middleware
       (keep identity)
       (map #(expand-middleware % meta opts))
       (keep identity)
       (apply comp identity)))

(defn gen [f & args]
  (->MiddlewareGenerator f args))

(defn compile-handler
  ([route opts]
   (compile-handler route opts nil))
  ([[path {:keys [middleware handler] :as meta}] opts scope]
   (ensure-handler! path meta scope)
   ((compose-middleware middleware meta opts) handler)))

(defn router
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:compile compile-handler} opts)]
     (reitit/router data opts))))
