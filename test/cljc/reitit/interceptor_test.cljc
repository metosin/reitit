(ns reitit.interceptor-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.interceptor :as interceptor]
            [reitit.core :as r])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(def ctx (interceptor/context []))

(defn execute [interceptors ctx]
  (as-> ctx $
        (reduce #(%2 %1) $ (keep :enter interceptors))
        (reduce #(%2 %1) $ (reverse (keep :leave interceptors)))
        (:response $)))

(defn f [value ctx]
  (update ctx :request conj value))

(defn kws [k qk]
  (keyword (namespace qk) (str (name k) "_" (name qk))))

(defn interceptor [value]
  {:name value
   :enter #(update % :request (fnil conj []) (kws :enter value))
   :leave #(update % :response (fnil conj []) (kws :leave value))})

(defn enter [value]
  {:name value
   :enter (partial f value)})

(defn handler [request]
  (conj request :ok))

(defn create
  ([interceptors]
   (create interceptors nil))
  ([interceptors opts]
   (let [chain (interceptor/chain
                 (conj interceptors handler)
                 :data opts)]
     (partial execute chain))))

(deftest expand-interceptor-test

  (testing "interceptor records"

    (testing "interceptor"
      (let [calls (atom 0)
            enter (fn [value]
                    (swap! calls inc)
                    {:enter (fn [ctx]
                              (update ctx :request conj value))})]

        (testing "as function"
          (reset! calls 0)
          (let [app (create [(enter :value)])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app ctx)))
              (is (= 1 @calls)))))

        (testing "as interceptor vector"
          (reset! calls 0)
          (let [app (create [[enter :value]])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app ctx)))
              (is (= 1 @calls)))))

        (testing "as keyword"
          (reset! calls 0)
          (let [app (create [:enter] {::interceptor/registry {:enter (enter :value)}})]
            (dotimes [_ 10]
              (is (= [:value :ok] (app ctx)))
              (is (= 1 @calls)))))

        (testing "missing keyword"
          (is (thrown-with-msg?
                ExceptionInfo
                #"Interceptor :enter not found in registry"
                (create [:enter]))))

        (testing "existing keyword, compiling to nil"
          (let [app (create [:enter] {::interceptor/registry {:enter {:compile (constantly nil)}}})]
            (is (= [:ok] (app ctx)))))

        (testing "as map"
          (reset! calls 0)
          (let [app (create [{:enter (:enter (enter :value))}])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app ctx)))
              (is (= 1 @calls)))))

        (testing "as Interceptor"
          (reset! calls 0)
          (let [app (create [(interceptor/map->Interceptor {:enter (:enter (enter :value))})])]
            (dotimes [_ 10]
              (is (= [:value :ok] (app ctx)))
              (is (= 1 @calls)))))))

    (testing "compiled interceptor"
      (let [calls (atom 0)
            i1 (fn [value]
                 {:compile (fn [data _]
                             (swap! calls inc)
                             {:enter (fn [ctx]
                                       (update ctx :request into [data value]))})})
            i3 (fn [value]
                 {:compile (fn [_ _]
                             (swap! calls inc)
                             {:compile (fn [_ _]
                                         (swap! calls inc)
                                         (i1 value))})})]

        (testing "as function"
          (reset! calls 0)
          (let [app (create [[i1 :value]])]
            (dotimes [_ 10]
              (is (= [:data :value :ok] (app ctx)))
              (is (= 1 @calls)))))

        (testing "as interceptor"
          (reset! calls 0)
          (let [app (create [(i1 :value)])]
            (dotimes [_ 10]
              (is (= [:data :value :ok] (app ctx)))
              (is (= 1 @calls)))))

        (testing "deeply compiled interceptor"
          (reset! calls 0)
          (let [app (create [[i3 :value]])]
            (dotimes [_ 10]
              (is (= [:data :value :ok] (app ctx)))
              (is (= 3 @calls)))))

        (testing "too deeply compiled interceptor fails"
          (binding [interceptor/*max-compile-depth* 2]
            (is (thrown?
                  ExceptionInfo
                  #"Too deep Interceptor compilation"
                  (create [[i3 :value]])))))

        (testing "nil unmounts the interceptor"
          (let [app (create [{:compile (constantly nil)}
                             {:compile (constantly nil)}])]
            (dotimes [_ 10]
              (is (= [:ok] (app ctx))))))))))

(defn create-app [router]
  (let [handler (interceptor/interceptor-handler router)]
    (fn [path]
      (when-let [interceptors (handler path)]
        (execute interceptors ctx)))))

(deftest interceptor-handler-test

  (testing "interceptor-handler"
    (let [api-interceptor (interceptor :api)
          router (interceptor/router
                   [["/ping" handler]
                    ["/api" {:interceptors [api-interceptor]}
                     ["/ping" handler]
                     ["/admin" {:interceptors [[interceptor :admin]]}
                      ["/ping" handler]]]])
          app (create-app router)]

      (testing "not found"
        (is (= nil (app "/favicon.ico"))))

      (testing "normal handler"
        (is (= [:ok] (app "/ping"))))

      (testing "with interceptor"
        (is (= [:enter_api :ok :leave_api] (app "/api/ping"))))

      (testing "with nested interceptor"
        (is (= [:enter_api :enter_admin :ok :leave_admin :leave_api] (app "/api/admin/ping"))))

      (testing ":compile interceptor can be unmounted at creation-time"
        (let [i1 {:name ::i1, :compile (constantly (interceptor ::i1))}
              i2 {:name ::i2, :compile (constantly nil)}
              i3 (interceptor ::i3)
              router (interceptor/router
                       ["/api" {:interceptors [i1 i2 i3 i2]
                                :handler handler}])
              app (create-app router)]

          (is (= [::enter_i1 ::enter_i3 :ok ::leave_i3 ::leave_i1] (app "/api")))

          (testing "routes contain list of actually applied interceptors"
            (is (= [::i1 ::i3 ::interceptor/handler]
                   (->> (r/compiled-routes router)
                        first
                        last
                        :interceptors
                        (map :name)))))

          (testing "match contains list of actually applied interceptors"
            (is (= [::i1 ::i3 ::interceptor/handler]
                   (->> "/api"
                        (r/match-by-path router)
                        :result
                        :interceptors
                        (map :name))))))))))

(deftest chain-test
  (testing "chain can produce interceptor chain of any IntoInterceptor"
    (let [i1 {:compile (constantly (interceptor ::i1))}
          i2 {:compile (constantly nil)}
          i3 (interceptor ::i3)
          i4 (interceptor ::i4)
          i5 {:compile (fn [{:keys [mount?]} _]
                         (when mount?
                           (interceptor ::i5)))}
          chain1 (interceptor/chain [i1 i2 i3 i4 i5 handler] {:mount? true})
          chain2 (interceptor/chain [i1 i2 i3 i4 i5 handler] {:mount? false})
          chain3 (interceptor/chain [i1 i2 i3 i4 i5] {:mount? false})]
      (is (= [::enter_i1 ::enter_i3 ::enter_i4 ::enter_i5 :ok ::leave_i5 ::leave_i4 ::leave_i3 ::leave_i1] (execute chain1 ctx)))
      (is (= [::enter_i1 ::enter_i3 ::enter_i4 :ok ::leave_i4 ::leave_i3 ::leave_i1] (execute chain2 ctx)))
      (is (= [::leave_i4 ::leave_i3 ::leave_i1] (execute chain3 ctx))))))

(deftest interceptor-transform-test
  (let [debug-i (enter ::debug)
        create (fn [options]
                 (create-app
                   (interceptor/router
                     ["/ping" {:interceptors [(enter ::olipa)
                                              (enter ::kerran)
                                              (enter ::avaruus)]
                               :handler handler}]
                     options)))
        inject-debug (interceptor/transform-butlast #(interleave % (repeat debug-i)))
        sort-interceptors (interceptor/transform-butlast (partial sort-by :name))]

    (testing "by default, all interceptors are applied in order"
      (let [app (create nil)]
        (is (= [::olipa ::kerran ::avaruus :ok] (app "/ping")))))

    (testing "interceptors can be re-ordered"
      (let [app (create {::interceptor/transform sort-interceptors})]
        (is (= [::avaruus ::kerran ::olipa :ok] (app "/ping")))))

    (testing "adding debug interceptor between interceptors"
      (let [app (create {::interceptor/transform inject-debug})]
        (is (= [::olipa ::debug ::kerran ::debug ::avaruus ::debug :ok] (app "/ping")))))

    (testing "vector of transformations"
      (let [app (create {::interceptor/transform [inject-debug
                                                  sort-interceptors]})]
        (is (= [::avaruus ::debug ::debug ::debug ::kerran ::olipa :ok] (app "/ping")))))))
