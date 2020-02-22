(ns reitit.ring-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.set :as set]
            [reitit.middleware :as middleware]
            [reitit.ring :as ring]
            [reitit.core :as r]
            [reitit.trie :as trie])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defn mw [handler name]
  (fn
    ([request]
     (handler (update request ::mw (fnil conj []) name)))
    ([request respond raise]
     (handler (update request ::mw (fnil conj []) name) respond raise))))

(defn mw-variadic [handler name name2 name3]
  (mw handler (keyword (str name "_" name2 "_" name3))))

(defn handler
  ([{::keys [mw]}]
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

  (testing "with top-level middleware"
    (let [router (ring/router
                   ["/api" {:middleware [[mw :api]]}
                    ["/get" {:get handler}]])
          app (ring/ring-handler router nil {:middleware [[mw :top]]})]

      (testing "router can be extracted"
        (is (= router (ring/get-router app))))

      (testing "not found"
        (is (= nil (app {:uri "/favicon.ico"}))))

      (testing "on match"
        (is (= {:status 200, :body [:top :api :ok]}
               (app {:uri "/api/get" :request-method :get}))))))

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
          (is (= name (-> (r/match-by-name router name) :data :name)))))))

  (testing "intermediate paths"
    (testing "without conflicts"
      (let [routes ["/"       {:get {:handler handler} :name ::root}
                    ["foo"    {:name ::foo}] ;; Inherits handler from above
                    ["bar"    {:get {:handler handler} :name ::bar}
                     ["/baz"  {:get {:handler handler} :name ::baz}]]
                    ["bang" {}
                     ["/bang"]]
                    ["ping"   {:name ::ping}
                     ["/pong" {:get {:handler handler} :name ::pong}]]]
            router (ring/router routes)
            match  #(r/match-by-path router %)]
        (are [path name]
          (is (= name (-> (match path) :data :name)))
          "/" ::root
          "/foo" ::foo
          "/bar" ::bar
          "/bar/baz" ::baz
          "/ping" ::ping
          "/ping/pong" ::pong)
        (is (nil? (match "/bang")))
        (is (-> (match "/bang/bang") :data :get :handler))))

    (testing "with conflicts"
      (let [routes ["/"       {:get {:handler handler} :name ::root}
                    [""       {:name ::other-root}] ;; Conflicts with ::root path
                    ["foo"    {:name ::foo}]
                    ["bar"    {:get {:handler handler} :name ::bar}
                     ["/baz"  {:get {:handler handler} :name ::baz}]]
                    ["ping"   {:name ::ping}
                     ["/pong" {:get {:handler handler} :name ::pong}]]]]
        (is (thrown-with-msg?
              ExceptionInfo
              #"Router contains conflicting route paths"
              (ring/router routes)))))))

(defn wrap-enforce-roles [handler]
  (fn [{::keys [roles] :as request}]
    (let [required (some-> request (ring/get-match) :data ::roles)]
      (if (and (seq required) (not (set/intersection required roles)))
        {:status 403, :body "forbidden"}
        (handler request)))))

(deftest mw-variadic-test
  (let [app (ring/ring-handler
              (ring/router
                ["/" {:middleware [[mw-variadic "kikka" "kakka" "kukka"]]
                      :handler handler}]))]
    (is (= {:status 200, :body [:kikka_kakka_kukka :ok]}
           (app {:request-method :get, :uri "/"})))))

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
            (is (= -406 (:status (app {:request-method :get, :uri "/pong"})))))))

      (testing "with some custom http responses"
        (let [app (ring/ring-handler router (ring/create-default-handler
                                             {:not-found (constantly {:status -404})}))]
          (testing "route doesn't match"
            (is (= 405 (:status (app {:request-method :post, :uri "/ping"}))))))))))

(deftest default-options-handler-test
  (let [response {:status 200, :body "ok"}]

    (testing "with defaults"
      (let [app (ring/ring-handler
                  (ring/router
                    [["/get" {:get (constantly response)
                              :post (constantly response)}]
                     ["/options" {:options (constantly response)}]
                     ["/any" (constantly response)]]))]

        (testing "endpoint with a non-options handler"
          (let [request {:request-method :options, :uri "/get"}]
            (is (= response (app {:request-method :get, :uri "/get"})))
            (is (= {:status 200, :body "", :headers {"Allow" "GET,POST,OPTIONS"}}
                   (app request)))
            (testing "3-arity"
              (let [result (atom nil)
                    respond (partial reset! result)
                    raise ::not-called]
                (app request respond raise)
                (is (= {:status 200, :body "", :headers {"Allow" "GET,POST,OPTIONS"}}
                       @result))))))

        (testing "endpoint with a options handler"
          (is (= response (app {:request-method :options, :uri "/options"}))))

        (testing "endpoint with top-level handler"
          (is (= response (app {:request-method :get, :uri "/any"})))
          (is (= response (app {:request-method :options, :uri "/any"}))))))

    (testing "disabled via options"
      (let [app (ring/ring-handler
                  (ring/router
                    [["/get" {:get (constantly response)}]
                     ["/options" {:options (constantly response)}]
                     ["/any" (constantly response)]]
                    {::ring/default-options-handler nil}))]

        (testing "endpoint with a non-options handler"
          (is (= response (app {:request-method :get, :uri "/get"})))
          (is (= nil (app {:request-method :options, :uri "/get"}))))

        (testing "endpoint with a options handler"
          (is (= response (app {:request-method :options, :uri "/options"}))))

        (testing "endpoint with top-level handler"
          (is (= response (app {:request-method :get, :uri "/any"})))
          (is (= response (app {:request-method :options, :uri "/any"}))))))))

(deftest trailing-slash-handler-test
  (let [ok {:status 200, :body "ok"}
        routes [["" {:summary "unreachable"
                     :get (constantly ok)}]
                ["/slash-less" {:get (constantly ok),
                                :post (constantly ok)}]
                ["/with-slash/" {:get (constantly ok),
                                 :post (constantly ok)}]]]
    (testing "using :method :add"
      (let [app (ring/ring-handler
                  (ring/router routes)
                  (ring/redirect-trailing-slash-handler {:method :add}))]

        (testing "exact matches work"
          (is (= ok (app {:request-method :get, :uri "/slash-less"})))
          (is (= ok (app {:request-method :post, :uri "/slash-less"})))
          (is (= ok (app {:request-method :get, :uri "/with-slash/"})))
          (is (= ok (app {:request-method :post, :uri "/with-slash/"}))))

        (testing "adds slashes"
          (is (= 301 (:status (app {:request-method :get, :uri "/with-slash"}))))
          (is (= 308 (:status (app {:request-method :post, :uri "/with-slash"})))))

        (testing "does not strip slashes"
          (is (= nil (app {:request-method :get, :uri "/slash-less/"})))
          (is (= nil (app {:request-method :post, :uri "/slash-less/"}))))))

    (testing "using :method :strip"
      (let [app (ring/ring-handler
                  (ring/router routes)
                  (ring/redirect-trailing-slash-handler {:method :strip}))]

        (testing "stripping to empty string doesn't match"
          (is (= nil (:status (app {:request-method :get, :uri "/"})))))

        (testing "exact matches work"
          (is (= ok (app {:request-method :get, :uri "/slash-less"})))
          (is (= ok (app {:request-method :post, :uri "/slash-less"})))
          (is (= ok (app {:request-method :get, :uri "/with-slash/"})))
          (is (= ok (app {:request-method :post, :uri "/with-slash/"}))))

        (testing "does not add slashes"
          (is (= nil (app {:request-method :get, :uri "/with-slash"})))
          (is (= nil (app {:request-method :post, :uri "/with-slash"}))))

        (testing "strips slashes"
          (is (= 301 (:status (app {:request-method :get, :uri "/slash-less/"}))))
          (is (= 308 (:status (app {:request-method :post, :uri "/slash-less/"})))))

        (testing "strips multiple slashes"
          (is (= 301 (:status (app {:request-method :get, :uri "/slash-less/////"}))))
          (is (= 308 (:status (app {:request-method :post, :uri "/slash-less//"})))))))

    (testing "without option (equivalent to using :method :both)"
      (let [app (ring/ring-handler
                  (ring/router routes)
                  (ring/redirect-trailing-slash-handler))]

        (testing "exact matches work"
          (is (= ok (app {:request-method :get, :uri "/slash-less"})))
          (is (= ok (app {:request-method :post, :uri "/slash-less"})))
          (is (= ok (app {:request-method :get, :uri "/with-slash/"})))
          (is (= ok (app {:request-method :post, :uri "/with-slash/"}))))

        (testing "adds slashes"
          (is (= 301 (:status (app {:request-method :get, :uri "/with-slash"}))))
          (is (= 308 (:status (app {:request-method :post, :uri "/with-slash"})))))

        (testing "strips slashes"
          (is (= 301 (:status (app {:request-method :get, :uri "/slash-less/"}))))
          (is (= 308 (:status (app {:request-method :post, :uri "/slash-less/"})))))

        (testing "strips multiple slashes"
          (is (= 301 (:status (app {:request-method :get, :uri "/slash-less/////"}))))
          (is (= 308 (:status (app {:request-method :post, :uri "/slash-less//"})))))))))

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
        handler (fn [{::keys [mw]}] {:status 200 :body (conj mw :ok)})
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

(deftest router-available-in-default-branch
  (testing "1-arity"
    ((ring/ring-handler
       (ring/router [])
       (fn [{::r/keys [router]}]
         (is router)))
     {}))
  (testing "3-arity"
    ((ring/ring-handler
       (ring/router [])
       (fn [{::r/keys [router]} _ _]
         (is router)))
     {} ::respond ::raise)))

#?(:clj
   (deftest invalid-path-parameters-parsing-concurrent-requests-277-test
     (testing "in enough concurrent system, path-parameters can bleed"
       (doseq [compiler [trie/java-trie-compiler trie/clojure-trie-compiler]]
         (let [app (ring/ring-handler
                     (ring/router
                       ["/:id" (fn [request]
                                 {:status 200
                                  :body (-> request :path-params :id)})])
                     {::trie/trie-compiler compiler})]
           (dotimes [_ 10]
             (future
               (dotimes [n 100000]
                 (let [body (:body (app {:request-method :get, :uri (str "/" n)}))]
                   (is (= body (str n))))))))))))
