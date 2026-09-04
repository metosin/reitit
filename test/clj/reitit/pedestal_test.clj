(ns reitit.pedestal-test
  (:require [clojure.test :refer [deftest is testing]]
            [io.pedestal.http]
            [io.pedestal.test]
            [reitit.http :as http]
            [reitit.http.interceptors.exception :as exception]
            [reitit.pedestal :as pedestal]))

(deftest arities-test
  (is (= #{0} (#'pedestal/arities (fn []))))
  (is (= #{1} (#'pedestal/arities (fn [_]))))
  (is (= #{0 1 2} (#'pedestal/arities (fn ([]) ([_]) ([_ _])))))
  (testing "variadic fns report their required arity"
    (is (= #{0} (#'pedestal/arities (fn [& _]))))
    (is (= #{1} (#'pedestal/arities (fn [_ & _]))))
    (is (= #{1 2} (#'pedestal/arities (fn ([_]) ([_ _ & _]))))))
  (testing ":arglists metadata is preferred over reflection"
    (is (= #{2} (#'pedestal/arities (with-meta (fn [& _]) {:arglists '([_ _])}))))
    (is (= #{0 1} (#'pedestal/arities (with-meta (fn [& _]) {:arglists '([] [_])}))))))

(deftest accepts-arity?-test
  (testing "fixed arities"
    (is (#'pedestal/accepts-arity? (fn [_ _]) 2))
    (is (not (#'pedestal/accepts-arity? (fn [_]) 2)))
    (is (not (#'pedestal/accepts-arity? (fn [_ _ _]) 2))))
  (testing "variadic arities accept their required arity or more"
    (is (#'pedestal/accepts-arity? (fn [& _]) 2))
    (is (#'pedestal/accepts-arity? (fn [_ & _]) 2))
    (is (not (#'pedestal/accepts-arity? (fn [_ _ _ & _]) 2))))
  (testing ":arglists metadata is preferred over reflection"
    (is (#'pedestal/accepts-arity? (with-meta (fn [& _]) {:arglists '([_ _])}) 2))
    (is (#'pedestal/accepts-arity? (with-meta (fn [& _]) {:arglists '([_ & _])}) 2))
    (is (not (#'pedestal/accepts-arity? (with-meta (fn [& _]) {:arglists '([_])}) 2)))))

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

(deftest pedestal-e2e-test
  (let [router (pedestal/routing-interceptor
                (http/router
                 [""
                  {:interceptors [{:name :nop} (exception/exception-interceptor)]}
                  ["/ok" (fn [_] {:status 200, :body "ok"})]
                  ["/fail" (fn [_] (throw (ex-info "kosh" {})))]]))
        service (-> {:io.pedestal.http/request-logger nil
                     :io.pedestal.http/routes []}
                    (io.pedestal.http/default-interceptors)
                    (pedestal/replace-last-interceptor router)
                    (io.pedestal.http/create-servlet)
                    (:io.pedestal.http/service-fn))]
    (is (= "ok" (:body (io.pedestal.test/response-for service :get "/ok"))))
    (is (= 500 (:status (io.pedestal.test/response-for service :get "/fail"))))))

(deftest pedestal-inject-router-test
  (let [check-router (fn [r] (when-not (:reitit.core/router r)
                               (throw (ex-info "Missing :reitit.core/router!" {}))))
        interceptor {:name ::needs-router
                     :enter (fn [{:as context :keys [request]}]
                              (check-router request)
                              context)}
        router (pedestal/routing-interceptor
                (http/router
                 [""
                  ["/ok" (fn [r] (check-router r) {:status 200, :body "ok"})]])
                nil
                {:interceptors [interceptor]})
        service (-> {:io.pedestal.http/request-logger nil
                     :io.pedestal.http/routes []}
                    (io.pedestal.http/default-interceptors)
                    (pedestal/replace-last-interceptor router)
                    (io.pedestal.http/create-servlet)
                    (:io.pedestal.http/service-fn))]
    (is (= "ok" (:body (io.pedestal.test/response-for service :get "/ok"))))
    (is (= "Not Found" (:body (io.pedestal.test/response-for service :get "/not-existing"))))))
