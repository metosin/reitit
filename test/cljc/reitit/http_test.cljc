(ns reitit.http-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.set :as set]
            [reitit.interceptor :as interceptor]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.core :as r])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defn interceptor [name]
  {:enter (fn [ctx] (update-in ctx [:request ::i] (fnil conj []) name))})

(defn handler [{:keys [::i]}]
  {:status 200 :body (conj i :ok)})

(deftest http-router-test

  (testing "http-handler"
    (let [api-interceptor (interceptor :api)
          router (http/router
                   ["/api" {:interceptors [api-interceptor]}
                    ["/all" handler]
                    ["/get" {:get handler}]
                    ["/users" {:interceptors [[interceptor :users]]
                               :get handler
                               :post {:handler handler
                                      :interceptors [[interceptor :post]]}
                               :handler handler}]])
          app (http/ring-handler router nil {:executor sieppari/executor})]

      (testing "router can be extracted"
        (is (= (r/routes router)
               (r/routes (http/get-router app)))))

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
    (let [router (http/router
                   [["/api"
                     ["/all" {:handler handler :name ::all}]
                     ["/get" {:get {:handler handler :name ::HIDDEN}
                              :name ::get}]
                     ["/users" {:get handler
                                :post handler
                                :handler handler
                                :name ::users}]]])
          app (http/ring-handler router nil {:executor sieppari/executor})]

      (testing "router can be extracted"
        (is (= (r/routes router)
               (r/routes (http/get-router app)))))

      (testing "only top-level route names are matched"
        (is (= [::all ::get ::users]
               (r/route-names router))))

      (testing "all named routes can be matched"
        (doseq [name (r/route-names router)]
          (is (= name (-> (r/match-by-name router name) :data :name))))))))

(def enforce-roles-interceptor
  {:enter (fn [{{:keys [::roles] :as request} :request :as ctx}]
            (let [required (some-> request (http/get-match) :data ::roles)]
              (if (and (seq required) (not (set/intersection required roles)))
                (-> ctx
                    (assoc :response {:status 403, :body "forbidden"})
                    (assoc :queue nil))
                ctx)))})

(deftest enforcing-data-rules-at-runtime-test
  (let [handler (constantly {:status 200, :body "ok"})
        app (http/ring-handler
              (http/router
                [["/api"
                  ["/ping" handler]
                  ["/admin" {::roles #{:admin}}
                   ["/ping" handler]]]]
                {:data {:interceptors [enforce-roles-interceptor]}})
              nil {:executor sieppari/executor})]

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
        router (http/router
                 [["/ping" {:get (constantly response)}]
                  ["/pong" (constantly nil)]])
        app (http/ring-handler router nil {:executor sieppari/executor})]

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
        (let [app (http/ring-handler
                    router
                    (ring/create-default-handler)
                    {:executor sieppari/executor})]
          (testing "route doesn't match yields 404"
            (is (= 404 (:status (app {:request-method :get, :uri "/"})))))
          (testing "method doesn't match yields 405"
            (is (= 405 (:status (app {:request-method :post, :uri "/ping"})))))
          (testing "handler rejects yields nil"
            (is (= 406 (:status (app {:request-method :get, :uri "/pong"})))))))

      (testing "with custom http responses"
        (let [app (http/ring-handler
                    router
                    (ring/create-default-handler
                      {:not-found (constantly {:status -404})
                       :method-not-allowed (constantly {:status -405})
                       :not-acceptable (constantly {:status -406})})
                    {:executor sieppari/executor})]
          (testing "route doesn't match"
            (is (= -404 (:status (app {:request-method :get, :uri "/"})))))
          (testing "method doesn't match"
            (is (= -405 (:status (app {:request-method :post, :uri "/ping"})))))
          (testing "handler rejects"
            (is (= -406 (:status (app {:request-method :get, :uri "/pong"}))))))))))

#_(deftest async-http-test
    (let [promise #(let [value (atom ::nil)]
                     (fn
                       ([] @value)
                       ([x] (reset! value x))))
          response {:status 200, :body "ok"}
          router (http/router
                   [["/ping" {:get (fn [_ respond _]
                                     (respond response))}]
                    ["/pong" (fn [_ respond _]
                               (respond nil))]])
          app (http/ring-handler router)]

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

          (let [app (http/ring-handler router (ring/create-default-handler))]
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

#_(deftest middleware-transform-test
    (let [middleware (fn [name] {:name name
                                 :wrap (fn [handler]
                                         (fn [request]
                                           (handler (update request ::mw (fnil conj []) name))))})
          handler (fn [{:keys [::mw]}] {:status 200 :body (conj mw :ok)})
          request {:uri "/api/avaruus" :request-method :get}
          create (fn [options]
                   (http/ring-handler
                     (http/router
                       ["/api" {:interceptors [(middleware :olipa)]}
                        ["/avaruus" {:interceptors [(middleware :kerran)]
                                     :get {:handler handler
                                           :interceptors [(middleware :avaruus)]}}]]
                       options)))]

      (testing "by default, all middleware are applied in order"
        (let [app (create nil)]
          (is (= {:status 200, :body [:olipa :kerran :avaruus :ok]}
                 (app request)))))

      (testing "middleware can be re-ordered"
        (let [app (create {::interceptor/transform (partial sort-by :name)})]
          (is (= {:status 200, :body [:avaruus :kerran :olipa :ok]}
                 (app request)))))

      (testing "adding debug middleware between middleware"
        (let [app (create {::interceptor/transform #(interleave % (repeat (middleware "debug")))})]
          (is (= {:status 200, :body [:olipa "debug" :kerran "debug" :avaruus "debug" :ok]}
                 (app request)))))))

#?(:clj
   (deftest resource-handler-test
     (let [redirect (fn [uri] {:status 302, :body "", :headers {"Location" uri}})
           request (fn [uri] {:uri uri, :request-method :get})]
       (testing "inside a router"

         (testing "from root"
           (let [app (http/ring-handler
                       (http/router
                         ["/*" (ring/create-resource-handler)])
                       (ring/create-default-handler)
                       {:executor sieppari/executor})]
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
           (let [app (http/ring-handler
                       (http/router
                         ["/files/*" (ring/create-resource-handler)])
                       (ring/create-default-handler)
                       {:executor sieppari/executor})
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
           (let [app (http/ring-handler
                       (http/router [])
                       (ring/routes
                         (ring/create-resource-handler {:path "/"})
                         (ring/create-default-handler))
                       {:executor sieppari/executor})]
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
           (let [app (http/ring-handler
                       (http/router [])
                       (ring/routes
                         (ring/create-resource-handler {:path "/files"})
                         (ring/create-default-handler))
                       {:executor sieppari/executor})
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
