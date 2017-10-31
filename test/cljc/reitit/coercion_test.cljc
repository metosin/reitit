(ns reitit.coercion-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.coercion.spec :as spec])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defn handler
  ([{:keys [::mw]}]
   {:status 200 :body (conj mw :ok)})
  ([request respond raise]
   (respond (handler request))))

(deftest coercion-test
  (let [app (ring/ring-handler
              (ring/router
                ["/api"
                 ["/plus/:e"
                  {:get {:parameters {:query {:a int?}
                                      :body {:b int?}
                                      :form {:c int?}
                                      :header {:d int?}
                                      :path {:e int?}}
                         :responses {200 {:schema {:total pos-int?}}}
                         :handler (fn [{{{:keys [a]} :query
                                         {:keys [b]} :body
                                         {:keys [c]} :form
                                         {:keys [d]} :header
                                         {:keys [e]} :path} :parameters}]
                                    {:status 200
                                     :body {:total (+ a b c d e)}})}}]]
                {:meta {:middleware [coercion/gen-wrap-coerce-parameters
                                     coercion/gen-wrap-coerce-response]
                        :coercion spec/coercion}}))]

    (testing "all good"
      (is (= {:status 200
              :body {:total 15}}
             (app {:uri "/api/plus/5"
                   :request-method :get
                   :query-params {"a" "1"}
                   :body-params {:b 2}
                   :form-params {:c 3}
                   :header-params {:d 4}}))))

    (testing "invalid request"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Request coercion failed"
            (app {:uri "/api/plus/5"
                  :request-method :get}))))

    (testing "invalid response"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Response coercion failed"
            (app {:uri "/api/plus/5"
                  :request-method :get
                  :query-params {"a" "1"}
                  :body-params {:b 2}
                  :form-params {:c 3}
                  :header-params {:d -40}}))))))
