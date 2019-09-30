(ns reitit.ring.middleware.session-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring.middleware.session :as session]
            [ring.middleware.session.memory :as memory]
            [reitit.ring :as ring]))

(defn get-session-id
  "Parse the session-id out of response headers."
  [request]
  (let [pattern  #"ring-session=([-\w]+);Path=/;HttpOnly"
        parse-fn (partial re-find pattern)]
    (-> request
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
    (is (= (count @store)
           1))
    (is (-> @store first second)
        {:counter 2})))

(deftest default-session-test
  (let [app             (ring/ring-handler
                         (ring/router
                          ["/api"
                           {:middleware [session/session-middleware]}
                           ["/ping" handler]
                           ["/pong" handler]]))
        first-response  (app {:request-method :get
                              :uri            "/api/ping"})
        session-id      (get-session-id first-response)
        second-response (app {:request-method :get
                              :uri            "/api/pong"
                              :cookies        {"ring-session" {:value session-id}}})]
    (is (= (inc (get-in first-response [:body :counter]))
           (get-in second-response [:body :counter])))))
