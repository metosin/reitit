(ns reitit.middleware-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.middleware :as middleware]
            [clojure.set :as set]
            [reitit.core :as r])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(deftest expand-middleware-test

  (testing "middleware records"

    (testing ":wrap & :compile are exclusive"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Middleware can't have both :wrap and :compile defined"
            (middleware/create
              {:name ::test
               :wrap identity
               :compile (constantly identity)}))))

    (testing "middleware"
      (let [calls (atom 0)
            wrap (fn [handler value]
                   (swap! calls inc)
                   (fn [request]
                     [value request]))
            ->app (fn [ast handler]
                    (middleware/compile-handler
                      (middleware/expand ast :data {})
                      handler))]

        (testing "as middleware function"
          (reset! calls 0)
          (let [app (->app [[#(wrap % :value)]] identity)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))

        (testing "as middleware vector"
          (reset! calls 0)
          (let [app (->app [[wrap :value]] identity)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))

        (testing "as map"
          (reset! calls 0)
          (let [app (->app [[{:wrap #(wrap % :value)}]] identity)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))

        (testing "as map vector"
          (reset! calls 0)
          (let [app (->app [[{:wrap wrap} :value]] identity)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))

        (testing "as Middleware"
          (reset! calls 0)
          (let [app (->app [[(middleware/create {:wrap #(wrap % :value)})]] identity)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))

        (testing "as Middleware vector"
          (reset! calls 0)
          (let [app (->app [[(middleware/create {:wrap wrap}) :value]] identity)]
            (dotimes [_ 10]
              (is (= [:value :request] (app :request)))
              (is (= 1 @calls)))))))

    (testing "compiled Middleware"
      (let [calls (atom 0)
            mw {:compile (fn [data _]
                           (swap! calls inc)
                           (fn [handler value]
                             (swap! calls inc)
                             (fn [request]
                               [data value request])))}
            mw3 {:compile (fn [data _]
                            (swap! calls inc)
                            {:compile (fn [data _]
                                        (swap! calls inc)
                                        mw)})}
            ->app (fn [ast handler]
                    (middleware/compile-handler
                      (middleware/expand ast :data {})
                      handler))]

        (testing "as map"
          (reset! calls 0)
          (let [app (->app [[mw :value]] identity)]
            (dotimes [_ 10]
              (is (= [:data :value :request] (app :request)))
              (is (= 2 @calls)))))

        (testing "as Middleware"
          (reset! calls 0)
          (let [app (->app [[(middleware/create mw) :value]] identity)]
            (dotimes [_ 10]
              (is (= [:data :value :request] (app :request)))
              (is (= 2 @calls)))))

        (testing "deeply compiled Middleware"
          (reset! calls 0)
          (let [app (->app [[(middleware/create mw3) :value]] identity)]
            (dotimes [_ 10]
              (is (= [:data :value :request] (app :request)))
              (is (= 4 @calls)))))

        (testing "too deeply compiled Middleware fails"
          (binding [middleware/*max-compile-depth* 2]
            (is (thrown? Exception (->app [[(middleware/create mw3) :value]] identity)))))

        (testing "nil unmounts the middleware"
          (let [app (->app [{:compile (constantly nil)}
                            {:compile (constantly nil)}] identity)]
            (dotimes [_ 10]
              (is (= :request (app :request))))))))))

(defn create-app [router]
  (let [h (middleware/middleware-handler router)]
    (fn [path]
      (if-let [f (h path)]
        (f [])))))

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
            (is (= [::mw1 ::mw3] (->> (r/routes router)
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
  (testing "chain can produce middlware chain of any IntoMiddleware"
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
        (is (= [::olipa ::debug ::kerran ::debug ::avaruus ::debug :ok] (app "/ping")))))))
