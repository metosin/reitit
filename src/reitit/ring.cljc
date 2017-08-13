(ns reitit.ring
  (:require [reitit.core :as reitit]))

(defprotocol ExpandMiddleware
  (expand-middleware [this]))

(extend-protocol ExpandMiddleware

  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (expand-middleware [[f & args]]
    (fn [handler]
      (apply f handler args)))

  #?(:clj  clojure.lang.Fn
     :cljs function)
  (expand-middleware [this] this)

  nil
  (expand-middleware [_]))

(defn compile-handler [[path {:keys [middleware handler] :as meta}]]
  (when-not handler
    (throw (ex-info
             (str "path '" path "' doesn't have a :handler defined")
             {:path path, :meta meta})))
  (let [wrap (->> middleware
                  (keep identity)
                  (map expand-middleware)
                  (apply comp identity))]
    (wrap handler)))

(defn router [data]
  (reitit/router data {:compile compile-handler}))

(defn ring-handler [router]
  (fn
    ([request]
     (if-let [match (reitit/match-by-path router (:uri request))]
       ((:handler match) request)))
    ([request respond raise]
     (if-let [match (reitit/match-by-path router (:uri request))]
       ((:handler match) request respond raise)))))
