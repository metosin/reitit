(ns reitit.http.interceptors.muuntaja-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.http :as http]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.swagger :as swagger]
            [reitit.interceptor.sieppari :as sieppari]
            [muuntaja.core :as m]))

(deftest muuntaja-test
  (let [data {:kikka "kukka"}
        app (http/ring-handler
              (http/router
                ["/ping" {:get (constantly {:status 200, :body data})}]
                {:data {:muuntaja m/instance
                        :interceptors [(muuntaja/format-interceptor)]}})
              {:executor sieppari/executor})]
    (is (= data (->> {:request-method :get, :uri "/ping"}
                     (app)
                     :body
                     (m/decode m/instance "application/json"))))))

(deftest muuntaja-swagger-test
  (let [with-defaults m/instance
        no-edn-decode (m/create (-> m/default-options (update-in [:formats "application/edn"] dissoc :decoder)))
        just-edn (m/create (-> m/default-options (m/select-formats ["application/edn"])))
        app (http/ring-handler
              (http/router
                [["/defaults"
                  {:get identity}]
                 ["/explicit-defaults"
                  {:muuntaja with-defaults
                   :get identity}]
                 ["/no-edn-decode"
                  {:muuntaja no-edn-decode
                   :get identity}]
                 ["/just-edn"
                  {:muuntaja just-edn
                   :get identity}]
                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]
                {:data {:muuntaja m/instance
                        :interceptors [(muuntaja/format-interceptor)]}})
              {:executor sieppari/executor})
        spec (fn [path]
               (let [path (keyword path)]
                 (-> {:request-method :get :uri "/swagger.json"}
                     (app) :body
                     (->> (m/decode m/instance "application/json"))
                     :paths path :get)))
        produces (comp set :produces spec)
        consumes (comp set :consumes spec)]

    (testing "with defaults"
      (let [path "/defaults"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)
               (consumes path)))))

    (testing "with explicit muuntaja defaults"
      (let [path "/explicit-defaults"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)
               (consumes path)))))

    (testing "without edn decode"
      (let [path "/no-edn-decode"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)))
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"}
               (consumes path)))))

    (testing "just edn"
      (let [path "/just-edn"]
        (is (= #{"application/edn"}
               (produces path)
               (consumes path)))))))

(deftest muuntaja-swagger-parts-test
  (let [app (http/ring-handler
              (http/router
                [["/request"
                  {:interceptors [(muuntaja/format-negotiate-interceptor)
                                  (muuntaja/format-request-interceptor)]
                   :get identity}]
                 ["/response"
                  {:interceptors [(muuntaja/format-negotiate-interceptor)
                                  (muuntaja/format-response-interceptor)]
                   :get identity}]
                 ["/both"
                  {:interceptors [(muuntaja/format-negotiate-interceptor)
                                  (muuntaja/format-response-interceptor)
                                  (muuntaja/format-request-interceptor)]
                   :get identity}]

                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]
                {:data {:muuntaja m/instance}})
              {:executor sieppari/executor})
        spec (fn [path]
               (-> {:request-method :get :uri "/swagger.json"}
                   (app) :body :paths (get path) :get))
        produces (comp :produces spec)
        consumes (comp :consumes spec)]

    (testing "just request formatting"
      (let [path "/request"]
        (is (nil? (produces path)))
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (consumes path)))))

    (testing "just response formatting"
      (let [path "/response"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)))
        (is (nil? (consumes path)))))

    (testing "just response formatting"
      (let [path "/both"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)))
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (consumes path)))))))
