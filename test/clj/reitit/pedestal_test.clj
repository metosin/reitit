(ns reitit.pedestal-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.pedestal :as pedestal]))

(deftest arities-test
  (is (= #{0} (#'pedestal/arities (fn []))))
  (is (= #{1} (#'pedestal/arities (fn [_]))))
  (is (= #{0 1 2} (#'pedestal/arities (fn ([]) ([_]) ([_ _]))))))

(deftest interceptor-test
  (testing "without :enter, :leave or :error are stripped"
    (is (nil? (pedestal/->interceptor {:name ::kikka}))))
  (testing ":error arities are wrapped"
    (let [has-2-arity-error? (fn [interceptor]
                               (-> interceptor
                                   (pedestal/->interceptor)
                                   (:error)
                                   (#'pedestal/arities)
                                   (contains? 2)))]
      (is (has-2-arity-error? {:error (fn [_])}))
      (is (has-2-arity-error? {:error (fn [_ _])}))
      (is (has-2-arity-error? {:error (fn [_ _ _])}))
      (is (has-2-arity-error? {:error (fn ([_]) ([_ _]))})))))
