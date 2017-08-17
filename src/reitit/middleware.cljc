(ns reitit.middleware
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.core :as reitit]))

(defprotocol ExpandMiddleware
  (expand-middleware [this opts]))

(extend-protocol ExpandMiddleware

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (expand-middleware [[f & args] _]
    (fn [handler]
      (apply f handler args)))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (expand-middleware [this _] this)

  nil
  (expand-middleware [_ _]))

(defn- ensure-handler! [path meta scope]
  (when-not (:handler meta)
    (throw (ex-info
             (str "path \"" path "\" doesn't have a :handler defined"
                  (if scope (str " for " scope)))
             (merge {:path path, :meta meta}
                    (if scope {:scope scope}))))))

(defn compose-middleware [middleware opts]
  (->> middleware
       (keep identity)
       (map #(expand-middleware % opts))
       (apply comp identity)))

(defn compile-handler
  ([route opts]
   (compile-handler route opts nil))
  ([[path {:keys [middleware handler] :as meta}] opts scope]
   (ensure-handler! path meta scope)
   ((compose-middleware middleware opts) handler)))

(defn router
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:compile compile-handler} opts)]
     (reitit/router data opts))))
