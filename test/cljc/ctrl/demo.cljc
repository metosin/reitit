(ns ctrl.demo
  (:require [reitit.core :as r]
            [reitit.ring :as ring]
            [ctrl.merge :as cm]
            [ctrl.apply :as ca]))

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
                          :handler (constantly {:status 200, :body "ok"})}}]]])
    (ring/ring-handler)
    (ring/get-router)
    (r/compiled-routes)
    (last)
    (last)
    :get
    :data)

(def path-map [[[:parameters any?] vector]
               [[any? :parameters any?] vector]
               [[:responses any? :body] vector]
               [[any? :responses any? :body] vector]])

;; using apply as pre-merge
(cm/merge
 (ca/apply
  {:parameters {:query [:map [:x :int]]}
   :get {:parameters {:query [:map [:x :int]]}
         :responses {200 {:body [:map [:total :int]]}}}}
  path-map)
 (ca/apply
  {:parameters {:query [:map [:y :int]]}
   :get {:parameters {:query [:map [:y :int]]}
         :responses {200 {:body [:map [:total :int]]}}}
   :post {:parameters {:query [:map [:y :int]]}}}
  path-map))
;{:get {:responses {200 {:body [[:map [:total :int]]
;                               [:map [:total :int]]]}},
;       :parameters {:query [[:map [:x :int]]
;                            [:map [:y :int]]]}},
; :parameters {:query [[:map [:x :int]]
;                      [:map [:y :int]]]},
; :post {:parameters {:query [[:map [:y :int]]]}}}
