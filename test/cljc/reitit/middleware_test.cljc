(ns reitit.middleware-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.middleware :as middleware]
            [reitit.core :as r])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(def request [])

(defn handler [request]
  (conj request :ok))

(defn create
  ([middleware]
   (create middleware nil))
  ([middleware opts]
   (middleware/chain
     middleware
     handler
     :data
     opts)))

(deftest expand-middleware-test

  (testing "middleware records"

    (testing "middleware"
      (let [calls (atom 0)
            wrap (fn [handler value]
                   (swap! calls inc)
                   (fn [request]
                     (handler (conj request value))))]

        (testing "as function"
          (reset! calls 0)
          (let [app (create [#(wrap % :value)])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))

        (testing "as function vector"
          (reset! calls 0)
          (let [app (create [[#(wrap % :value)]])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))

        (testing "as keyword"
          (reset! calls 0)
          (let [app (create [:wrap] {::middleware/registry {:wrap #(wrap % :value)}})]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))

        (testing "as keyword vector"
          (reset! calls 0)
          (let [app (create [[:wrap :value]] {::middleware/registry {:wrap wrap}})]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))

        (testing "missing keyword"
          (is (thrown-with-msg?
                ExceptionInfo
                #"Middleware :wrap not found in registry"
                (create [:wrap]))))

        (testing "existing keyword, compiling to nil"
          (let [app (create [:wrap] {::middleware/registry {:wrap {:compile (constantly nil)}}})]
            (is (= [:ok] (app request)))))

        (testing "as function vector with value(s)"
          (reset! calls 0)
          (let [app (create [[wrap :value]])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))

        (testing "as map"
          (reset! calls 0)
          (let [app (create [[{:wrap #(wrap % :value)}]])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))

        (testing "as map vector"
          (reset! calls 0)
          (let [app (create [[{:wrap wrap} :value]])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))

        (testing "as Middleware"
          (reset! calls 0)
          (let [app (create [[(middleware/map->Middleware {:wrap #(wrap % :value)})]])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))

        (testing "as Middleware vector"
          (reset! calls 0)
          (let [app (create [[(middleware/map->Middleware {:wrap wrap}) :value]])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app request)))
              (is (= 1 @calls)))))))

    (testing "compiled Middleware"
      (let [calls (atom 0)
            mw {:compile (fn [data _]
                           (swap! calls inc)
                           (fn [handler value]
                             (swap! calls inc)
                             (fn [request]
                               (handler (into request [data value])))))}
            mw3 {:compile (fn [data _]
                            (swap! calls inc)
                            {:compile (fn [data _]
                                        (swap! calls inc)
                                        mw)})}]

        (testing "as map"
          (reset! calls 0)
          (let [app (create [[mw :value]])]
            (dotimes [_ 10]
              (is (= [:data :value :ok] (app request)))
              (is (= 2 @calls)))))

        (testing "as Middleware"
          (reset! calls 0)
          (let [app (create [[(middleware/map->Middleware mw) :value]])]
            (dotimes [_ 10]
              (is (= [:data :value :ok] (app request)))
              (is (= 2 @calls)))))

        (testing "deeply compiled Middleware"
          (reset! calls 0)
          (let [app (create [[(middleware/map->Middleware mw3) :value]])]
            (dotimes [_ 10]
              (is (= [:data :value :ok] (app request)))
              (is (= 4 @calls)))))

        (testing "too deeply compiled Middleware fails"
          (binding [middleware/*max-compile-depth* 2]
            (is (thrown?
                  ExceptionInfo
                  #"Too deep Middleware compilation"
                  (create [[(middleware/map->Middleware mw3) :value]])))))

        (testing "nil unmounts the middleware"
          (let [app (create [{:compile (constantly nil)}
                             {:compile (constantly nil)}])]
            (dotimes [_ 10]
              (is (= [:ok] (app request))))))))))

(defn create-app [router]
  (let [h (middleware/middleware-handler router)]
    (fn [path]
      (if-let [f (h path)]
        (f request)))))

(deftest middleware-handler-test

  (testing "all paths should have a handler"
    (is (thrown-with-msg?
          ExceptionInfo
          #"path \"/ping\" doesn't have a :handler defined"
          (middleware/router ["/ping"]))))

  (testing "middleware-handler"
    (let [mw (fn [handler value]
               (fn [request]
                 (conj (handler (conj request value)) value)))
          api-mw #(mw % :api)
          handler #(conj % :ok)
          router (middleware/router
                   [["/ping" handler]
                    ["/api" {:middleware [api-mw]}
                     ["/ping" handler]
                     ["/admin" {:middleware [[mw :admin]]}
                      ["/ping" handler]]]])
          app (create-app router)]

      (testing "not found"
        (is (= nil (app "/favicon.ico"))))

      (testing "normal handler"
        (is (= [:ok] (app "/ping"))))

      (testing "with middleware"
        (is (= [:api :ok :api] (app "/api/ping"))))

      (testing "with nested middleware"
        (is (= [:api :admin :ok :admin :api] (app "/api/admin/ping"))))

      (testing ":compile middleware can be unmounted at creation-time"
        (let [mw1 {:name ::mw1, :compile (constantly #(mw % ::mw1))}
              mw2 {:name ::mw2, :compile (constantly nil)}
              mw3 {:name ::mw3, :wrap #(mw % ::mw3)}
              router (middleware/router
                       ["/api" {:name ::api
                                :middleware [mw1 mw2 mw3 mw2]
                                :handler handler}])
              app (create-app router)]

          (is (= [::mw1 ::mw3 :ok ::mw3 ::mw1] (app "/api")))

          (testing "routes contain list of actually applied mw"
            (is (= [::mw1 ::mw3] (->> (r/compiled-routes router)
                                      first
                                      last
                                      :middleware
                                      (map :name)))))

          (testing "match contains list of actually applied mw"
            (is (= [::mw1 ::mw3] (->> "/api"
                                      (r/match-by-path router)
                                      :result
                                      :middleware
                                      (map :name))))))))))

(deftest chain-test
  (testing "chain can produce middleware chain of any IntoMiddleware"
    (let [mw (fn [handler value]
               #(conj (handler (conj % value)) value))
          handler #(conj % :ok)
          mw1 {:compile (constantly #(mw % ::mw1))}
          mw2 {:compile (constantly nil)}
          mw3 {:wrap #(mw % ::mw3)}
          mw4 #(mw % ::mw4)
          mw5 {:compile (fn [{:keys [mount?]} _]
                          (when mount?
                            #(mw % ::mw5)))}
          chain1 (middleware/chain [mw1 mw2 mw3 mw4 mw5] handler {:mount? true})
          chain2 (middleware/chain [mw1 mw2 mw3 mw4 mw5] handler {:mount? false})]
      (is (= [::mw1 ::mw3 ::mw4 ::mw5 :ok ::mw5 ::mw4 ::mw3 ::mw1] (chain1 [])))
      (is (= [::mw1 ::mw3 ::mw4 :ok ::mw4 ::mw3 ::mw1] (chain2 []))))))

(deftest middleware-transform-test
  (let [wrap (fn [handler value]
               #(handler (conj % value)))
        debug-mw {:name ::debug, :wrap #(wrap % ::debug)}
        create (fn [options]
                 (create-app
                   (middleware/router
                     ["/ping" {:middleware [{:name ::olipa, :wrap #(wrap % ::olipa)}
                                            {:name ::kerran, :wrap #(wrap % ::kerran)}
                                            {:name ::avaruus, :wrap #(wrap % ::avaruus)}]
                               :handler #(conj % :ok)}]
                     options)))]

    (testing "by default, all middleware are applied in order"
      (let [app (create nil)]
        (is (= [::olipa ::kerran ::avaruus :ok] (app "/ping")))))

    (testing "middleware can be re-ordered"
      (let [app (create {::middleware/transform (partial sort-by :name)})]
        (is (= [::avaruus ::kerran ::olipa :ok] (app "/ping")))))

    (testing "adding debug middleware between middleware"
      (let [app (create {::middleware/transform #(interleave % (repeat debug-mw))})]
        (is (= [::olipa ::debug ::kerran ::debug ::avaruus ::debug :ok] (app "/ping")))))

    (testing "vector of transformations"
      (let [app (create {::middleware/transform [#(interleave % (repeat debug-mw))
                                                 (partial sort-by :name)]})]
        (is (= [::avaruus ::debug ::debug ::debug ::kerran ::olipa :ok] (app "/ping")))))))
