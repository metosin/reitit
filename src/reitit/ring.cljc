(ns reitit.ring
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.middleware :as middleware]
            [reitit.core :as reitit]))

(def http-methods #{:get :head :patch :delete :options :post :put})
(defrecord MethodHandlers [get head patch delete options post put])

(defn- group-keys [meta]
  (reduce-kv
    (fn [[top childs] k v]
      (if (http-methods k)
        [top (assoc childs k v)]
        [(assoc top k v) childs])) [{} {}] meta))

(defn ring-handler [router]
  (with-meta
    (fn
      ([request]
       (if-let [match (reitit/match-by-path router (:uri request))]
         ((:handler match) (assoc request ::match match))))
      ([request respond raise]
       (if-let [match (reitit/match-by-path router (:uri request))]
         ((:handler match) (assoc request ::match match) respond raise))))
    {::router router}))

(defn get-router [handler]
  (some-> handler meta ::router))

(defn get-match [request]
  (::match request))

(defn coerce-handler [[path meta] {:keys [expand]}]
  [path (reduce
          (fn [acc method]
            (if (contains? acc method)
              (update acc method expand)
              acc)) meta http-methods)])

(defn compile-handler [[path meta] opts]
  (let [[top childs] (group-keys meta)]
    (if-not (seq childs)
      (middleware/compile-handler [path meta] opts)
      (let [handlers (map->MethodHandlers
                       (reduce-kv
                         #(assoc %1 %2 (middleware/compile-handler
                                         [path (meta-merge top %3)] opts %2))
                         {} childs))
            default-handler (if (:handler top) (middleware/compile-handler [path meta] opts))
            resolved-handler #(or (% handlers) default-handler)]
        (fn
          ([request]
           (if-let [handler (resolved-handler (:request-method request))]
             (handler request)))
          ([request respond raise]
           (if-let [handler (resolved-handler (:request-method request))]
             (handler request respond raise))))))))

(defn router
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:coerce coerce-handler
                           :compile compile-handler} opts)]
     (reitit/router data opts))))
