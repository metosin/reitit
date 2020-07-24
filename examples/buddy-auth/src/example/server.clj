(ns example.server
  "This example demonstrates how to use Buddy authentication with Reitit
  to implement simple authentication and authorization flows.

  HTTP Basic authentication is used to authenticate with username and
  password. HTTP Basic authentication middleware checks credentials
  against a 'database' and if credentials are OK, a signed jwt-token
  is created and returned. The token can be used to call endpoints
  that require token authentication. Token payload contains users
  roles that can be used for authorization.

  NOTE: This example is not production-ready."
  (:require [buddy.auth :as buddy-auth]
            [buddy.auth.backends :as buddy-auth-backends]
            [buddy.auth.backends.httpbasic :as buddy-auth-backends-httpbasic]
            [buddy.auth.middleware :as buddy-auth-middleware]
            [buddy.hashers :as buddy-hashers]
            [buddy.sign.jwt :as jwt]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]))

(def db
  "We use a simple map as a db here but in real-world you would
  interface with a real data storage in `basic-auth` function."
  {"user1"
   {:id       1
    :password (buddy-hashers/encrypt "kissa13")
    :roles    ["admin" "user"]}
   "user2"
   {:id       2
    :password (buddy-hashers/encrypt "koira12")
    :roles    ["user"]}})

(def private-key
  "Used for signing and verifying JWT-tokens In real world you'd read
  this from an environment variable or some other configuration that's
  not included in the source code."
  "kana15")

(defn create-token
  "Creates a signed jwt-token with user data as payload.
  `valid-seconds` sets the expiration span."
  [user & {:keys [valid-seconds] :or {valid-seconds 7200}}] ;; 2 hours
  (let [payload (-> user
                    (select-keys [:id :roles])
                    (assoc :exp (.plusSeconds
                                 (java.time.Instant/now) valid-seconds)))]
    (jwt/sign payload private-key {:alg :hs512})))

(def token-backend
  "Backend for verifying JWT-tokens."
  (buddy-auth-backends/jws {:secret private-key :options {:alg :hs512}}))

(defn basic-auth
  "Authentication function called from basic-auth middleware for each
  request. The result of this function will be added to the request
  under key :identity.

  NOTE: Use HTTP Basic authentication always with HTTPS in real setups."
  [db request {:keys [username password]}]
  (let [user (get db username)]
    (if (and user (buddy-hashers/check password (:password user)))
      (-> user
          (dissoc :password)
          (assoc :token (create-token user)))
      false)))

(defn create-basic-auth-backend
  "Creates basic-auth backend to be used by basic-auth-middleware."
  [db]
  (buddy-auth-backends-httpbasic/http-basic-backend
   {:authfn (partial basic-auth db)}))

(defn create-basic-auth-middleware
  "Creates a middleware that authenticates requests using http-basic
  authentication."
  [db]
  (let [backend (create-basic-auth-backend db)]
    (fn [handler]
      (buddy-auth-middleware/wrap-authentication handler backend))))

(defn token-auth-middleware
  "Middleware used on routes requiring token authentication."
  [handler]
  (buddy-auth-middleware/wrap-authentication handler token-backend))

(defn admin-middleware
  "Middleware used on routes requiring :admin role."
  [handler]
  (fn [request]
    (if (-> request :identity :roles set (contains? "admin"))
      (handler request)
      {:status 403 :body {:error "Admin role required"}})))

(defn auth-middleware
  "Middleware used in routes that require authentication. If request is
  not authenticated a 401 unauthorized response will be
  returned. Buddy checks if request key :identity is set to truthy
  value by any previous middleware."
  [handler]
  (fn [request]
    (if (buddy-auth/authenticated? request)
      (handler request)
      {:status 401 :body {:error "Unauthorized"}})))

(def routes
  [["/no-auth"
    [""
     {:get (fn [_] {:status 200 :body {:message "No auth succeeded!"}})}]]

   ["/basic-auth"
    [""
     {:middleware [(create-basic-auth-middleware db) auth-middleware]
      :get
      (fn [req]
        {:status 200
         :body
         {:message "Basic auth succeeded!"
          :user    (-> req :identity)}})}]]

   ["/token-auth"
    [""
     {:middleware [token-auth-middleware auth-middleware]
      :get        (fn [_] {:status 200 :body {:message "Token auth succeeded!"}})}]]

   ["/token-auth-with-admin-role"
    [""
     {:middleware [token-auth-middleware
                   auth-middleware
                   admin-middleware]
      :get        (fn [_] {:status 200 :body {:message "Token auth with admin role succeeded!"}})}]]])

(def app
  (ring/ring-handler
    (ring/router
      routes
      {:data
       {:muuntaja m/instance
        :middleware ; applied to all routes
        [params/wrap-params
         muuntaja/format-middleware
         coercion/coerce-exceptions-middleware
         coercion/coerce-request-middleware
         coercion/coerce-response-middleware]}})
    (ring/create-default-handler)))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))

(comment
  ;; Start server to try with real HTTP clients.
  (start)

  ;; ...or just execute following sexps in the REPL. :)

  (def headers {"accept" "application/edn"})
  (def read-body (comp read-string slurp :body))

  (-> {:headers headers :request-method :get :uri "/no-auth"}
      app
      read-body)
  ;; => {:message "No auth succeeded!"}

  (-> {:headers headers :request-method :get :uri "/basic-auth"}
      app
      read-body)
  ;; => {:error "Unauthorized"}

  (-> {:headers headers :request-method :get :uri "/token-auth"}
      app
      read-body)
  ;; => {:error "Unauthorized"}

  (import java.util.Base64)

  (defn ->base64
    "Encodes a string as base64."
    [s]
    (.encodeToString (Base64/getEncoder) (.getBytes s)))

  (defn basic-auth-headers [user pass]
    (merge headers {:authorization (str "Basic " (->base64 (str user ":" pass)))}))

  (def bad-creds (basic-auth-headers "juum" "joo"))
  (-> {:headers bad-creds :request-method :get :uri "/basic-auth"}
      app
      read-body)
  ;; => {:error "Unauthorized"}

  (def admin-creds (basic-auth-headers "user1" "kissa13"))

  (-> {:headers admin-creds :request-method :get :uri "/basic-auth"}
      app
      read-body)
  ;; {:message "Basic auth succeeded!",
  ;;  :user
  ;;  {:id 1,
  ;;   :roles [:admin :user],
  ;;   :token
  ;;   "eyJhbGciOiJIUzUxMiJ9.eyJp....."

  (def admin-token
    (-> {:headers admin-creds :request-method :get :uri "/basic-auth"}
        app
        read-body
        :user
        :token))

  (def user-creds (basic-auth-headers "user2" "koira12"))

  (def user-token
    (-> {:headers user-creds :request-method :get :uri "/basic-auth"}
        app
        read-body
        :user
        :token))

  (defn token-auth-headers [token]
    (merge headers {:authorization (str "Token " token)}))

  (def user-token-headers (token-auth-headers user-token))

  (-> {:headers user-token-headers :request-method :get :uri "/token-auth"}
      app
      read-body)
  ;; => {:message "Token auth succeeded!"}

  (-> {:headers user-token-headers :request-method :get :uri "/token-auth-with-admin-role"}
      app
      read-body)
  ;; => {:error "Admin role required"}

  (def admin-token-headers (token-auth-headers admin-token))

  (-> {:headers admin-token-headers :request-method :get :uri "/token-auth-with-admin-role"}
      app
      read-body)
  ;; => {:message "Token auth with admin role succeeded!"}
  )
