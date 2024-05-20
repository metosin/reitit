(ns reitit.ring.middleware.session
  (:require
   [clojure.spec.alpha :as s]
   [ring.middleware.session :as session]
   [ring.middleware.session.store :as session-store]
   [ring.middleware.session.memory :as memory]))

(s/def ::store #(satisfies? session-store/SessionStore %))
(s/def ::root string?)
(s/def ::cookie-name string?)
(s/def ::cookie-attrs map?)
(s/def ::session (s/keys :opt-un [::store ::root ::cookie-name ::cookie-attrs]))
(s/def ::spec (s/keys :opt-un [::session]))

(def ^:private store
  "The default shared in-memory session store.

  This is used when no `:store` key is provided to the middleware."
  (memory/memory-store (atom {})))

(def session-middleware
  "Middleware for session.

  Enter:
  Add the `:session` key into the request map based on the `:cookies`
  in the request map.

  Exit:
  When `:session` key presents in the response map, update the session
  store with its value. Then remove `:session` from the response map.

  | key          | description |
  | -------------|-------------|
  | `:session`   | A map of options that passes into the [`ring.middleware.session/wrap-session](http://ring-clojure.github.io/ring/ring.middleware.session.html#var-wrap-session) function`, or an empty map for the default options. The absence of this value will disable the middleware."
  {:name    :session
   :spec    ::spec
   :compile (fn [{session-opts :session} _]
              (if session-opts
                (let [session-opts (merge {:store store} session-opts)]
                  {:wrap #(session/wrap-session % session-opts)})))})
