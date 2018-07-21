(ns reitit.ring.middleware.muuntaja-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.ring.middleware.alpha.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [reitit.swagger :as swagger]
            [reitit.core :as r]))

(deftest muuntaja-test
  (let [data {:kikka "kukka"}
        app (ring/ring-handler
              (ring/router
                ["/ping" {:get (constantly {:status 200, :body data})}]
                {:data {:middleware [(muuntaja/create-format-middleware)]}}))]
    (is (= data (->> {:request-method :get, :uri "/ping"}
                     (app)
                     :body
                     (m/decode m/instance "application/json"))))))

(deftest muuntaja-swagger-test
  (let [with-defaults m/instance
        no-edn-decode (m/create (-> m/default-options (update-in [:formats "application/edn"] dissoc :decoder)))
        just-edn (m/create (-> m/default-options (m/select-formats ["application/edn"])))
        app (ring/ring-handler
              (ring/router
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
                {:data {:middleware [(muuntaja/create-format-middleware)]}}))
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
