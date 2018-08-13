(ns reitit.http
  (:require [meta-merge.core :refer [meta-merge]]
            [reitit.interceptor :as interceptor]
            [reitit.ring :as ring]
            [reitit.core :as r]
            [reitit.impl :as impl]))

(defrecord Endpoint [data handler path method interceptors])

(defn http-handler
  "Creates a ring-handler out of a http-router and
  an interceptor runner.
  Optionally takes a ring-handler which is called
  in no route matches."
  ([router runner]
   (http-handler router runner nil))
  ([router runner default-handler]
   (let [default-handler (or default-handler (fn ([_]) ([_ respond _] (respond nil))))]
     (with-meta
       (fn [request]
         (if-let [match (r/match-by-path router (:uri request))]
           (let [method (:request-method request)
                 path-params (:path-params match)
                 result (:result match)
                 interceptors (-> result method :interceptors)
                 request (-> request
                             (impl/fast-assoc :path-params path-params)
                             (impl/fast-assoc ::r/match match)
                             (impl/fast-assoc ::r/router router))]
             (:response (runner interceptors request)))
           (default-handler request)))
       {::r/router router}))))

(defn get-router [handler]
  (-> handler meta ::r/router))

(defn get-match [request]
  (::r/match request))

(defn coerce-handler [[path data] {:keys [expand] :as opts}]
  [path (reduce
          (fn [acc method]
            (if (contains? acc method)
              (update acc method expand opts)
              acc)) data ring/http-methods)])

(defn compile-result [[path data] opts]
  (let [[top childs] (ring/group-keys data)
        ->handler (fn [handler]
                    (if handler
                      (fn [ctx]
                        (->> ctx :request handler (assoc ctx :response)))))
        compile (fn [[path data] opts scope]
                  (let [data (update data :handler ->handler)]
                    (interceptor/compile-result [path data] opts scope)))
        ->endpoint (fn [p d m s]
                     (-> (compile [p d] opts s)
                         (map->Endpoint)
                         (assoc :path p)
                         (assoc :method m)))
        ->methods (fn [any? data]
                    (reduce
                      (fn [acc method]
                        (cond-> acc
                                any? (assoc method (->endpoint path data method nil))))
                      (ring/map->Methods {})
                      ring/http-methods))]
    (if-not (seq childs)
      (->methods true top)
      (reduce-kv
        (fn [acc method data]
          (let [data (meta-merge top data)]
            (assoc acc method (->endpoint path data method method))))
        (->methods (:handler top) data)
        childs))))

(defn router
  "Creates a [[reitit.core/Router]] from raw route data and optionally an options map with
  support for http-methods and Interceptors. See [docs](https://metosin.github.io/reitit/)
  for details.

  Example:

      (router
        [\"/api\" {:interceptors [format-i oauth2-i]}
          [\"/users\" {:get get-user
                       :post update-user
                       :delete {:interceptors [delete-i]
                               :handler delete-user}}]])

  See router options from [[reitit.core/router]] and [[reitit.middleware/router]]."
  ([data]
   (router data nil))
  ([data opts]
   (let [opts (meta-merge {:coerce coerce-handler, :compile compile-result} opts)]
     (r/router data opts))))

(ns reitit.interceptor.simple)

(defn enqueue [ctx interceptors]
  (update ctx :queue (fnil into clojure.lang.PersistentQueue/EMPTY) interceptors))


(defn leave [ctx stack key]
  (let [it (clojure.lang.RT/iter stack)]
    (loop [ctx ctx, key key]
      (if (.hasNext it)
        (if-let [f (-> it .next key)]
          (let [[ctx key] (try
                            [(f ctx)])])
          (recur
            (try
              (leave ctx)))
          (recur ctx))
        ctx))))

(defn try-f [ctx f]
  (try
    (f ctx)
    (catch Exception e
      (assoc ctx :error e))))

(defn enter [ctx]
  (let [queue ^clojure.lang.PersistentQueue (:queue ctx)
        stack (:stack ctx)
        error (:error ctx)
        interceptor (peek queue)]
    (cond

      ;; all done
      (not interceptor)
      (leave ctx stack :leave)

      ;; error
      error
      (leave (assoc ctx :queue nil) stack :error)

      ;; continue
      :else
      (let [queue (pop queue)
            stack (conj stack interceptor)
            f (or (:enter interceptor) identity)
            ctx (-> ctx (assoc :queue queue) (assoc :stack stack) (try-f f))]
        (recur ctx)))))

(defrecord Context [queue stack request response])

(defn context [interceptors request]
  (->Context (into clojure.lang.PersistentQueue/EMPTY interceptors) nil request nil))

(defn execute [interceptors request]
  (enter (context interceptors request)))

(ns user)

(require '[reitit.http :as http])
(require '[reitit.interceptor.simple :as simple])

(def i (fn [value]
         {:enter (fn [ctx]
                   (update-in ctx [:request :enter] (fnil conj []) value))
          :leave (fn [ctx]
                   (update-in ctx [:response :body :leave] (fnil conj []) value))}))

(def f (fn [key] {key (fn [ctx] (throw (ex-info "fail" {})))}))

(def app
  (http/http-handler
    (http/router
      ["/api"
       {:interceptors [(i 1) (i 2)]}
       ["/ipa"
        {:interceptors [(i 3) (f :enter) (i 4)]
         :handler (fn [{:keys [enter]}]
                    {:status 200
                     :body {:enter enter}})}]])
    simple/execute))

(app {:request-method :get, :uri "/api/ipa"})
; => {:status 200, :body {:enter [1 2 3 4], :leave [4 3 2 1]}}

(def app2
  (http/http-handler
    (http/router
      ["/api"
       {:interceptors [(i 1) (i 2)]}
       ["/ipa"
        {:interceptors [(i 3) (i 4) (fn [ctx]
                                      (assoc ctx
                                        :response
                                        {:status 200
                                         :body {:enter (-> ctx
                                                           :request
                                                           :enter)}}))]}]])
    simple/execute))

(app2 {:request-method :get, :uri "/api/ipa"})
; => {:status 200, :body {:enter [1 2 3 4], :leave [4 3 2 1]}}
