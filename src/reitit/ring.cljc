(ns reitit.ring
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.core :as reitit]))

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

(defn- ensure-handler! [path meta method]
  (when-not (:handler meta)
    (throw (ex-info
             (str "path \"" path "\" doesn't have a :handler defined"
                  (if method (str " for method " method)))
             {:path path, :method method, :meta meta}))))

(defn- compose-middleware [middleware]
  (->> middleware
       (keep identity)
       (map expand-middleware)
       (apply comp identity)))

(defn- compile-handler
  ([route opts]
   (compile-handler route opts nil))
  ([[path {:keys [middleware handler] :as meta}] _ method]
   (ensure-handler! path meta method)
   ((compose-middleware middleware) handler)))

(defn simple-router [data]
  (reitit/router data {:compile compile-handler}))

(defn ring-handler [router]
  (with-meta
    (fn
      ([request]
       (if-let [match (reitit/match-by-path router (:uri request))]
         ((:handler match) request)))
      ([request respond raise]
       (if-let [match (reitit/match-by-path router (:uri request))]
         ((:handler match) request respond raise))))
    {::router router}))

(defn get-router [handler]
  (some-> handler meta ::router))

(def http-methods #{:get :head :patch :delete :options :post :put})
(defrecord MethodHandlers [get head patch delete options post put])

(defn- group-keys [meta]
  (reduce-kv
    (fn [[top childs] k v]
      (if (http-methods k)
        [top (assoc childs k v)]
        [(assoc top k v) childs])) [{} {}] meta))

(defn coerce-method-handler [[path meta] {:keys [expand]}]
  [path (reduce
          (fn [acc method]
            (if (contains? acc method)
              (update acc method expand)
              acc)) meta http-methods)])

(defn compile-method-handler [[path meta] opts]
  (let [[top childs] (group-keys meta)]
    (if-not (seq childs)
      (compile-handler [path meta] opts)
      (let [handlers (map->MethodHandlers
                       (reduce-kv
                         #(assoc %1 %2 (compile-handler [path (meta-merge top %3)] opts %2))
                         {} childs))
            default-handler (if (:handler top) (compile-handler [path meta] opts))
            resolved-handler (fn [method] (or (method handlers) default-handler))]
        (fn
          ([request]
           (if-let [handler (resolved-handler (:request-method request))]
             (handler request)))
          ([request respond raise]
           (if-let [handler (resolved-handler (:request-method request))]
             (handler request respond raise))))))))

(defn method-router [data]
  (reitit/router data {:coerce coerce-method-handler
                       :compile compile-method-handler}))
