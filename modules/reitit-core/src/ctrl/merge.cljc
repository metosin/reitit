(ns ctrl.merge
  (:refer-clojure :exclude [merge])
  (:require [clojure.core :as c]
            [clojure.set :as set]))

(defn- meta* [obj]
  (if #?(:clj  (instance? clojure.lang.IObj obj)
         :cljs (satisfies? IMeta obj))
    (meta obj)))

(defn- with-meta* [obj m]
  (if #?(:clj  (instance? clojure.lang.IObj obj)
         :cljs (satisfies? IWithMeta obj))
    (with-meta obj m)
    obj))

(defn- displace? [obj]
  (-> obj meta* :displace))

(defn- replace? [obj]
  (-> obj meta* :replace))

(defn- top-displace? [obj]
  (-> obj meta* :top-displace))

(defn- different-priority? [left right]
  (boolean (or (some (some-fn nil? displace? replace?) [left right])
               (top-displace? left))))

(defn- remove-top-displace [obj {:keys [::replace-nil]}]
  (cond replace-nil nil
        (top-displace? obj) obj
        :else (vary-meta obj dissoc :top-displace)))

(defn- pick-prioritized [left right options]
  (cond (nil? left) right
        (nil? right) (remove-top-displace left options)

        (top-displace? left) right

        (and (displace? left) ;; Pick the rightmost
             (displace? right)) ;; if both are marked as displaceable
        (with-meta* right
                    (c/merge (meta* left) (meta* right)))

        (and (replace? left) ;; Pick the rightmost
             (replace? right)) ;; if both are marked as replaceable
        (with-meta* right
                    (c/merge (meta* left) (meta* right)))

        (or (displace? left)
            (replace? right))
        (with-meta* right
                    (c/merge (-> left meta* (dissoc :displace))
                             (-> right meta* (dissoc :replace))))

        (or (replace? left)
            (displace? right))
        (with-meta* left
                    (c/merge (-> right meta* (dissoc :displace))
                             (-> left meta* (dissoc :replace))))))

(defn find-custom-merge [path path-map]
  (letfn [(match [x f] (cond (keyword? f) (= x f) (or (fn? f) (ifn? f)) (f x)))]
    (reduce (fn [_ [ps f]] (let [match (loop [[p & pr] path, [pp & ppr] ps]
                                         (cond (and p pp (match p pp)) (recur pr ppr)
                                               (= nil p pp) true))]
                             (when match (reduced f)))) nil path-map)))

;;
;; public api
;;

(defn any [_] true)

(defn merge
  ([] {})
  ([left] left)
  ([left right] (merge left right nil))
  ([left right {:keys [::path ::path-map] :as options}]
   (let [custom-merge (find-custom-merge path path-map)]
     (cond
       (different-priority? left right)
       (pick-prioritized left right options)

       custom-merge
       (custom-merge left right options)

       (and (map? left) (map? right))
       (let [merge-entry (fn [m e]
                           (let [k (key e) v (val e)]
                             (if (contains? m k)
                               (assoc m k (merge (get m k) v (update options ::path (fnil conj []) k)))
                               (assoc m k v))))
             merge2 (fn [m1 m2]
                      (reduce merge-entry (or m1 {}) (seq m2)))]
         (reduce merge2 [left right]))

       (and (set? left) (set? right))
       (set/union right left)

       (and (coll? left) (coll? right))
       (if (or (-> left meta :prepend)
               (-> right meta :prepend))
         (-> (into (empty left) (concat right left))
             (with-meta (c/merge (meta left)
                                 (select-keys (meta right) [:displace]))))
         (into (empty left) (concat left right)))

       :else right))))

;;
;; spike
;;

(comment
 (merge
  {:parameters {:query {:x 1}}}
  {:parameters {:query nil}}
  {::replace-nil true}))

(ns demo2)

(require '[reitit.ring :as ring]
         '[malli.util :as mu]
         '[reitit.core :as r]
         '[ctrl.merge :as cm])

(defn ring-path-map [f]
  [[[:parameters cm/any] f]
   [[ring/http-methods :parameters cm/any] f]
   [[:responses cm/any :body] f]
   [[ring/http-methods :responses cm/any :body] f]])

(defn malli-merge [x y _] (mu/merge x y))

(-> (ring/router
     ["/api" {:parameters {:header [:map ["Api" :string]]}}
      ["/math/:x" {:parameters {:path [:map [:x :int]]
                                :query [:map [:b :string]]
                                :header [:map ["Math" :string]]}
                   :responses {200 {:body [:map [:total :int]]}
                               500 {:description "fail"}}}
       ["/plus/:y" {:get {:parameters {:query ^:replace [:map [:a :int]]
                                       :body [:map [:b :int]]
                                       :header [:map ["Plus" :string]]
                                       :path [:map [:y :int]]}
                          :responses {200 {:body [:map [:total2 :int]]}
                                      500 {:description "fail"}}
                          :handler (constantly {:status 200, :body "ok"})}}]]]
     {:meta-merge #(cm/merge %1 %2 {::cm/path-map (ring-path-map malli-merge)})})
    (ring/ring-handler)
    (ring/get-router)
    (r/compiled-routes)
    (last)
    (last)
    :get
    :data)
;{:parameters {:header [:map
;                       ["Api" :string]
;                       ["Math" :string]
;                       ["Plus" :string]],
;              :path [:map
;                     [:x :int]
;                     [:y :int]],
;              :query [:map [:a :int]],
;              :body [:map [:b :int]]},
; :responses {200 {:body [:map
;                         [:total :int]
;                         [:total2 :int]]}
;             500 {:description "fail"}},
; :handler #object[clojure.core$constantly$fn__5740]}

(cm/merge
 {:parameters {:query [:map [:x :int]]}
  :get {:parameters {:query [:map [:x :int]]}
        :responses {200 {:body [:map [:total :int]]}}}}
 {:parameters {:query [:map [:y :int]]}
  :get {:parameters {:query [:map [:y :int]]}
        :responses {200 {:body [:map [:total :int]]}}}
  :post {:parameters {:query [:map [:y :int]]}}}
 {::cm/path-map [[[:parameters cm/any] malli-merge]
                 [[cm/any :parameters cm/any] malli-merge]
                 [[:responses cm/any :body] malli-merge]
                 [[cm/any :responses cm/any :body] malli-merge]]})
;{:parameters {:query [:map [:x :int] [:y :int]]},
; :get {:parameters {:query [:map [:x :int] [:y :int]]}
;       :responses {200 {:body [:map [:total :int]]}}},
; :post {:parameters {:query [:map [:y :int]]}}}
