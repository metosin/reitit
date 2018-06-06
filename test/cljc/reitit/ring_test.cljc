(ns reitit.ring-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.set :as set]
            [reitit.middleware :as middleware]
            [reitit.ring :as ring]
            [reitit.core :as r])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defn mw [handler name]
  (fn
    ([request]
     (handler (update request ::mw (fnil conj []) name)))
    ([request respond raise]
     (handler (update request ::mw (fnil conj []) name) respond raise))))

(defn handler
  ([{:keys [::mw]}]
   {:status 200 :body (conj mw :ok)})
  ([request respond _]
   (respond (handler request))))

(deftest ring-router-test

  (testing "all paths should have a handler"
    (is (thrown-with-msg?
          ExceptionInfo
          #"path \"/ping\" doesn't have a :handler defined for :get"
          (ring/router ["/ping" {:get {}}]))))

  (testing "ring-handler"
    (let [api-mw #(mw % :api)
          router (ring/router
                   ["/api" {:middleware [api-mw]}
                    ["/all" handler]
                    ["/get" {:get handler}]
                    ["/users" {:middleware [[mw :users]]
                               :get handler
                               :post {:handler handler
                                      :middleware [[mw :post]]}
                               :handler handler}]])
          app (ring/ring-handler router)]

      (testing "router can be extracted"
        (is (= router (ring/get-router app))))

      (testing "not found"
        (is (= nil (app {:uri "/favicon.ico"}))))

      (testing "catch all handler"
        (is (= {:status 200, :body [:api :ok]}
               (app {:uri "/api/all" :request-method :get}))))

      (testing "just get handler"
        (is (= {:status 200, :body [:api :ok]}
               (app {:uri "/api/get" :request-method :get})))
        (is (= nil (app {:uri "/api/get" :request-method :post}))))

      (testing "expanded method handler"
        (is (= {:status 200, :body [:api :users :ok]}
               (app {:uri "/api/users" :request-method :get}))))

      (testing "method handler with middleware"
        (is (= {:status 200, :body [:api :users :post :ok]}
               (app {:uri "/api/users" :request-method :post}))))

      (testing "fallback handler"
        (is (= {:status 200, :body [:api :users :ok]}
               (app {:uri "/api/users" :request-method :put}))))

      (testing "3-arity"
        (let [result (atom nil)
              respond (partial reset! result), raise ::not-called]
          (app {:uri "/api/users" :request-method :post} respond raise)
          (is (= {:status 200, :body [:api :users :post :ok]}
                 @result))))))

  (testing "named routes"
    (let [router (ring/router
                   [["/api"
                     ["/all" {:handler handler :name ::all}]
                     ["/get" {:get {:handler handler :name ::HIDDEN}
                              :name ::get}]
                     ["/users" {:get handler
                                :post handler
                                :handler handler
                                :name ::users}]]])
          app (ring/ring-handler router)]

      (testing "router can be extracted"
        (is (= router (ring/get-router app))))

      (testing "only top-level route names are matched"
        (is (= [::all ::get ::users]
               (r/route-names router))))

      (testing "all named routes can be matched"
        (doseq [name (r/route-names router)]
          (is (= name (-> (r/match-by-name router name) :data :name))))))))

(defn wrap-enforce-roles [handler]
  (fn [{:keys [::roles] :as request}]
    (let [required (some-> request (ring/get-match) :data ::roles)]
      (if (and (seq required) (not (set/intersection required roles)))
        {:status 403, :body "forbidden"}
        (handler request)))))

(deftest enforcing-data-rules-at-runtime-test
  (let [handler (constantly {:status 200, :body "ok"})
        app (ring/ring-handler
              (ring/router
                [["/api"
                  ["/ping" handler]
                  ["/admin" {::roles #{:admin}}
                   ["/ping" handler]]]]
                {:data {:middleware [wrap-enforce-roles]}}))]

    (testing "public handler"
      (is (= {:status 200, :body "ok"}
             (app {:uri "/api/ping" :request-method :get}))))

    (testing "runtime-enforced handler"
      (testing "without needed roles"
        (is (= {:status 403 :body "forbidden"}
               (app {:uri "/api/admin/ping"
                     :request-method :get}))))
      (testing "with needed roles"
        (is (= {:status 200, :body "ok"}
               (app {:uri "/api/admin/ping"
                     :request-method :get
                     ::roles #{:admin}})))))))

(deftest default-handler-test
  (let [response {:status 200, :body "ok"}
        router (ring/router
                 [["/ping" {:get (constantly response)}]
                  ["/pong" (constantly nil)]])
        app (ring/ring-handler router)]

    (testing "match"
      (is (= response (app {:request-method :get, :uri "/ping"}))))

    (testing "no match"
      (testing "with defaults"
        (testing "route doesn't match yields nil"
          (is (= nil (app {:request-method :get, :uri "/"}))))
        (testing "method doesn't match yields nil"
          (is (= nil (app {:request-method :post, :uri "/ping"}))))
        (testing "handler rejects yields nil"
          (is (= nil (app {:request-method :get, :uri "/pong"})))))

      (testing "with default http responses"
        (let [app (ring/ring-handler router (ring/create-default-handler))]
          (testing "route doesn't match yields 404"
            (is (= 404 (:status (app {:request-method :get, :uri "/"})))))
          (testing "method doesn't match yields 405"
            (is (= 405 (:status (app {:request-method :post, :uri "/ping"})))))
          (testing "handler rejects yields nil"
            (is (= 406 (:status (app {:request-method :get, :uri "/pong"})))))))

      (testing "with custom http responses"
        (let [app (ring/ring-handler router (ring/create-default-handler
                                              {:not-found (constantly {:status -404})
                                               :method-not-allowed (constantly {:status -405})
                                               :not-acceptable (constantly {:status -406})}))]
          (testing "route doesn't match"
            (is (= -404 (:status (app {:request-method :get, :uri "/"})))))
          (testing "method doesn't match"
            (is (= -405 (:status (app {:request-method :post, :uri "/ping"})))))
          (testing "handler rejects"
            (is (= -406 (:status (app {:request-method :get, :uri "/pong"}))))))))))

(deftest async-ring-test
  (let [promise #(let [value (atom ::nil)]
                   (fn
                     ([] @value)
                     ([x] (reset! value x))))
        response {:status 200, :body "ok"}
        router (ring/router
                 [["/ping" {:get (fn [_ respond _]
                                   (respond response))}]
                  ["/pong" (fn [_ respond _]
                             (respond nil))]])
        app (ring/ring-handler router)]

    (testing "match"
      (let [respond (promise)
            raise (promise)]
        (app {:request-method :get, :uri "/ping"} respond raise)
        (is (= response (respond)))
        (is (= ::nil (raise)))))

    (testing "no match"

      (testing "with defaults"
        (testing "route doesn't match"
          (let [respond (promise)
                raise (promise)]
            (app {:request-method :get, :uri "/"} respond raise)
            (is (= nil (respond)))
            (is (= ::nil (raise)))))
        (testing "method doesn't match"
          (let [respond (promise)
                raise (promise)]
            (app {:request-method :post, :uri "/ping"} respond raise)
            (is (= nil (respond)))
            (is (= ::nil (raise)))))
        (testing "handler rejects"
          (let [respond (promise)
                raise (promise)]
            (app {:request-method :get, :uri "/pong"} respond raise)
            (is (= nil (respond)))
            (is (= ::nil (raise))))))

      (testing "with default http responses"

        (let [app (ring/ring-handler router (ring/create-default-handler))]
          (testing "route doesn't match"
            (let [respond (promise)
                  raise (promise)]
              (app {:request-method :get, :uri "/"} respond raise)
              (is (= 404 (:status (respond))))
              (is (= ::nil (raise)))))
          (testing "method doesn't match"
            (let [respond (promise)
                  raise (promise)]
              (app {:request-method :post, :uri "/ping"} respond raise)
              (is (= 405 (:status (respond))))
              (is (= ::nil (raise)))))
          (testing "if handler rejects"
            (let [respond (promise)
                  raise (promise)]
              (app {:request-method :get, :uri "/pong"} respond raise)
              (is (= 406 (:status (respond))))
              (is (= ::nil (raise))))))))))

(deftest middleware-transform-test
  (let [middleware (fn [name] {:name name
                               :wrap (fn [handler]
                                       (fn [request]
                                         (handler (update request ::mw (fnil conj []) name))))})
        handler (fn [{:keys [::mw]}] {:status 200 :body (conj mw :ok)})
        request {:uri "/api/avaruus" :request-method :get}
        create (fn [options]
                 (ring/ring-handler
                   (ring/router
                     ["/api" {:middleware [(middleware :olipa)]}
                      ["/avaruus" {:middleware [(middleware :kerran)]
                                   :get {:handler handler
                                         :middleware [(middleware :avaruus)]}}]]
                     options)))]

    (testing "by default, all middleware are applied in order"
      (let [app (create nil)]
        (is (= {:status 200, :body [:olipa :kerran :avaruus :ok]}
               (app request)))))

    (testing "middleware can be re-ordered"
      (let [app (create {::middleware/transform (partial sort-by :name)})]
        (is (= {:status 200, :body [:avaruus :kerran :olipa :ok]}
               (app request)))))

    (testing "adding debug middleware between middleware"
      (let [app (create {::middleware/transform #(interleave % (repeat (middleware "debug")))})]
        (is (= {:status 200, :body [:olipa "debug" :kerran "debug" :avaruus "debug" :ok]}
               (app request)))))))

#?(:clj
   (deftest resource-handler-test
     (let [redirect (fn [uri] {:status 302, :body "", :headers {"Location" uri}})
           request (fn [uri] {:uri uri, :request-method :get})]
       (testing "inside a router"

         (testing "from root"
           (let [app (ring/ring-handler
                       (ring/router
                         ["/*" (ring/create-resource-handler)])
                       (ring/create-default-handler))]
             (testing test
               (testing "different file-types"
                 (let [response (app (request "/hello.json"))]
                   (is (= "application/json" (get-in response [:headers "Content-Type"])))
                   (is (get-in response [:headers "Last-Modified"]))
                   (is (= "{\"hello\": \"file\"}" (slurp (:body response)))))
                 (let [response (app (request "/hello.xml"))]
                   (is (= "text/xml" (get-in response [:headers "Content-Type"])))
                   (is (get-in response [:headers "Last-Modified"]))
                   (is (= "<xml><hello>file</hello></xml>\n" (slurp (:body response))))))

               (testing "index-files"
                 (let [response (app (request "/docs"))]
                   (is (= (redirect "/docs/index.html") response)))
                 (let [response (app (request "/docs/"))]
                   (is (= (redirect "/docs/index.html") response))))

               (testing "not found"
                 (let [response (app (request "/not-found"))]
                   (is (= 404 (:status response)))))

               (testing "3-arity"
                 (let [result (atom nil)
                       respond (partial reset! result)
                       raise ::not-called]
                   (app (request "/hello.xml") respond raise)
                   (is (= "text/xml" (get-in @result [:headers "Content-Type"])))
                   (is (get-in @result [:headers "Last-Modified"]))
                   (is (= "<xml><hello>file</hello></xml>\n" (slurp (:body @result)))))))))

         (testing "from path"
           (let [app (ring/ring-handler
                       (ring/router
                         ["/files/*" (ring/create-resource-handler)])
                       (ring/create-default-handler))
                 request #(request (str "/files" %))
                 redirect #(redirect (str "/files" %))]
             (testing test
               (testing "different file-types"
                 (let [response (app (request "/hello.json"))]
                   (is (= "application/json" (get-in response [:headers "Content-Type"])))
                   (is (get-in response [:headers "Last-Modified"]))
                   (is (= "{\"hello\": \"file\"}" (slurp (:body response)))))
                 (let [response (app (request "/hello.xml"))]
                   (is (= "text/xml" (get-in response [:headers "Content-Type"])))
                   (is (get-in response [:headers "Last-Modified"]))
                   (is (= "<xml><hello>file</hello></xml>\n" (slurp (:body response))))))

               (testing "index-files"
                 (let [response (app (request "/docs"))]
                   (is (= (redirect "/docs/index.html") response)))
                 (let [response (app (request "/docs/"))]
                   (is (= (redirect "/docs/index.html") response))))

               (testing "not found"
                 (let [response (app (request "/not-found"))]
                   (is (= 404 (:status response)))))

               (testing "3-arity"
                 (let [result (atom nil)
                       respond (partial reset! result)
                       raise ::not-called]
                   (app (request "/hello.xml") respond raise)
                   (is (= "text/xml" (get-in @result [:headers "Content-Type"])))
                   (is (get-in @result [:headers "Last-Modified"]))
                   (is (= "<xml><hello>file</hello></xml>\n" (slurp (:body @result))))))))))

       (testing "outside a router"

         (testing "from root"
           (let [app (ring/ring-handler
                       (ring/router [])
                       (ring/routes
                         (ring/create-resource-handler {:path "/"})
                         (ring/create-default-handler)))]
             (testing test
               (testing "different file-types"
                 (let [response (app (request "/hello.json"))]
                   (is (= "application/json" (get-in response [:headers "Content-Type"])))
                   (is (get-in response [:headers "Last-Modified"]))
                   (is (= "{\"hello\": \"file\"}" (slurp (:body response)))))
                 (let [response (app (request "/hello.xml"))]
                   (is (= "text/xml" (get-in response [:headers "Content-Type"])))
                   (is (get-in response [:headers "Last-Modified"]))
                   (is (= "<xml><hello>file</hello></xml>\n" (slurp (:body response))))))

               (testing "index-files"
                 (let [response (app (request "/docs"))]
                   (is (= (redirect "/docs/index.html") response)))
                 (let [response (app (request "/docs/"))]
                   (is (= (redirect "/docs/index.html") response))))

               (testing "not found"
                 (let [response (app (request "/not-found"))]
                   (is (= 404 (:status response)))))

               (testing "3-arity"
                 (let [result (atom nil)
                       respond (partial reset! result)
                       raise ::not-called]
                   (app (request "/hello.xml") respond raise)
                   (is (= "text/xml" (get-in @result [:headers "Content-Type"])))
                   (is (get-in @result [:headers "Last-Modified"]))
                   (is (= "<xml><hello>file</hello></xml>\n" (slurp (:body @result)))))))))

         (testing "from path"
           (let [app (ring/ring-handler
                       (ring/router [])
                       (ring/routes
                         (ring/create-resource-handler {:path "/files"})
                         (ring/create-default-handler)))
                 request #(request (str "/files" %))
                 redirect #(redirect (str "/files" %))]
             (testing test
               (testing "different file-types"
                 (let [response (app (request "/hello.json"))]
                   (is (= "application/json" (get-in response [:headers "Content-Type"])))
                   (is (get-in response [:headers "Last-Modified"]))
                   (is (= "{\"hello\": \"file\"}" (slurp (:body response)))))
                 (let [response (app (request "/hello.xml"))]
                   (is (= "text/xml" (get-in response [:headers "Content-Type"])))
                   (is (get-in response [:headers "Last-Modified"]))
                   (is (= "<xml><hello>file</hello></xml>\n" (slurp (:body response))))))

               (testing "index-files"
                 (let [response (app (request "/docs"))]
                   (is (= (redirect "/docs/index.html") response)))
                 (let [response (app (request "/docs/"))]
                   (is (= (redirect "/docs/index.html") response))))

               (testing "not found"
                 (let [response (app (request "/not-found"))]
                   (is (= 404 (:status response)))))

               (testing "3-arity"
                 (let [result (atom nil)
                       respond (partial reset! result)
                       raise ::not-called]
                   (app (request "/hello.xml") respond raise)
                   (is (= "text/xml" (get-in @result [:headers "Content-Type"])))
                   (is (get-in @result [:headers "Last-Modified"]))
                   (is (= "<xml><hello>file</hello></xml>\n" (slurp (:body @result)))))))))))))
