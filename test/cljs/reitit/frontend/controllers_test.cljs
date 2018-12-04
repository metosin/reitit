(ns reitit.frontend.controllers-test
  (:require [clojure.test :refer [deftest testing is are]]
            [reitit.frontend.controllers :as rfc]))

(deftest apply-controller-test
  (is (= :ok (rfc/apply-controller {:stop (fn [_] :ok)} :stop)))
  (is (= :ok (rfc/apply-controller {:start (fn [_] :ok)} :start))))

(deftest apply-controllers-test
  (let [log (atom [])
        controller-state (atom [])
        controller-1 {:start (fn [_] (swap! log conj :start-1))
                      :stop  (fn [_] (swap! log conj :stop-1))}
        controller-2 {:start (fn [_] (swap! log conj :start-2))
                      :stop  (fn [_] (swap! log conj :stop-2))}
        controller-3 {:start (fn [{:keys [foo]}] (swap! log conj [:start-3 foo]))
                      :stop  (fn [{:keys [foo]}] (swap! log conj [:stop-3 foo]))
                      :identity (fn [match]
                                  {:foo (-> match :parameters :path :foo)})}]

    (testing "single controller started"
      (swap! controller-state rfc/apply-controllers
             {:data {:controllers [controller-1]}})

      (is (= [:start-1] @log))
      (is (= [(assoc controller-1 ::rfc/identity nil)] @controller-state))
      (reset! log []))

    (testing "second controller started"
      (swap! controller-state rfc/apply-controllers
             {:data {:controllers [controller-1 controller-2]}})

      (is (= [:start-2] @log))
      (is (= [(assoc controller-1 ::rfc/identity nil)
              (assoc controller-2 ::rfc/identity nil)]
             @controller-state))
      (reset! log []))

    (testing "second controller replaced"
      (swap! controller-state rfc/apply-controllers
             {:data {:controllers [controller-1 controller-3]}
              :parameters {:path {:foo 5}}})

      (is (= [:stop-2 [:start-3 5]] @log))
      (is (= [(assoc controller-1 ::rfc/identity nil)
              (assoc controller-3 ::rfc/identity {:foo 5})]
             @controller-state))
      (reset! log []))

    (testing "controller parameter changed"
      (swap! controller-state rfc/apply-controllers
             {:data {:controllers [controller-1 controller-3]}
              :parameters {:path {:foo 1}}})

      (is (= [[:stop-3 5] [:start-3 1]] @log))
      (is (= [(assoc controller-1 ::rfc/identity nil)
              (assoc controller-3 ::rfc/identity {:foo 1})]
             @controller-state))
      (reset! log []))

    (testing "all controllers stopped"
      (swap! controller-state rfc/apply-controllers
             {:data {:controllers []}})

      (is (= [[:stop-3 1] :stop-1] @log))
      (is (= [] @controller-state))
      (reset! log []))))

(deftest controller-data-parameters
  (let [log (atom [])
        controller-state (atom [])
        static {:start (fn [params] (swap! log conj [:start-static]))
                :stop  (fn [params] (swap! log conj [:stop-static]))}
        controller {:start (fn [params] (swap! log conj [:start params]))
                    :stop  (fn [params] (swap! log conj [:stop params]))
                    :parameters {:path [:foo]}}]

    (testing "init"
      (swap! controller-state rfc/apply-controllers
             {:data {:controllers [static controller]}
              :parameters {:path {:foo 1}}})

      (is (= [[:start-static]
              [:start {:path {:foo 1}}]] @log))
      (is (= [(assoc static ::rfc/identity nil)
              (assoc controller ::rfc/identity {:path {:foo 1}})]
             @controller-state))
      (reset! log []))

    (testing "params change"
      (swap! controller-state rfc/apply-controllers
             {:data {:controllers [static controller]}
              :parameters {:path {:foo 5}}})

      (is (= [[:stop {:path {:foo 1}}]
              [:start {:path {:foo 5}}]] @log))
      (is (= [(assoc static ::rfc/identity nil)
              (assoc controller ::rfc/identity {:path {:foo 5}})]
             @controller-state))
      (reset! log []))

    (testing "stop"
      (swap! controller-state rfc/apply-controllers
             {:data {:controllers []}})

      (is (= [[:stop {:path {:foo 5}}]
              [:stop-static]] @log))
      (reset! log []))))
