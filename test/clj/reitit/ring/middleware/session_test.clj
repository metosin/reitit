(ns reitit.ring.middleware.session-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring.middleware.session :as session]
            [ring.middleware.session.memory :as memory]
            [reitit.spec :as rs]
            [reitit.ring :as ring]
            [reitit.ring.spec :as rrs]))

(defn get-session-id
  "Parse the session-id out of response headers."
  [request]
  (let [pattern  #"ring-session=([-\w]+);Path=/;HttpOnly"
        parse-fn (partial re-find pattern)]
    (some-> request
            (get-in [:headers "Set-Cookie"])
            first
            parse-fn
            second)))

(defn handler
  "The handler that increments the counter."
  [{session :session}]
  (let [counter (inc (:counter session 0))]
    {:status  200
     :body    {:counter counter}
     :session {:counter counter}}))

(deftest session-test
  (testing "Custom session store"
    (let [store           (atom {})
          app             (ring/ring-handler
                           (ring/router
                            ["/api"
                             {:session    {:store (memory/memory-store store)}
                              :middleware [session/session-middleware]}
                             ["/ping" handler]
                             ["/pong" handler]]))
          first-response  (app {:request-method :get
                                :uri            "/api/ping"})
          session-id      (get-session-id first-response)
          second-response (app {:request-method :get
                                :uri            "/api/pong"
                                :cookies        {"ring-session" {:value session-id}}})]
      (testing "shared across routes"
        (is (= (count @store)
               1))
        (is (-> @store first second)
            {:counter 2})))))

(deftest default-session-test
  (testing "Default session store"
    (let [app             (ring/ring-handler
                           (ring/router
                            ["/api"
                             {:middleware [session/session-middleware]
                              :session    {}}
                             ["/ping" handler]
                             ["/pong" handler]]))
          first-response  (app {:request-method :get
                                :uri            "/api/ping"})
          session-id      (get-session-id first-response)
          second-response (app {:request-method :get
                                :uri            "/api/pong"
                                :cookies        {"ring-session" {:value session-id}}})]
      (testing "shared across routes"
        (is (= (inc (get-in first-response [:body :counter]))
               (get-in second-response [:body :counter])))))))

(deftest default-session-off-test
  (testing "Default session middleware"
    (let [app  (ring/ring-handler
                (ring/router
                 ["/api"
                  {:middleware [session/session-middleware]}
                  ["/ping" handler]]))
          resp (app {:request-method :get
                     :uri            "/api/ping"})]
      (testing "off by default"
        (is (nil? (get-session-id resp)))))))

(deftest session-spec-test
  (testing "Session spec"
    (testing "with invalid session store type"
      (is
       (thrown? Exception
                (ring/ring-handler
                 (ring/router
                  ["/api"
                   {:session    {:store nil}
                    :middleware [session/session-middleware]
                    :handler    handler}]
                  {:validate rrs/validate})))))))
