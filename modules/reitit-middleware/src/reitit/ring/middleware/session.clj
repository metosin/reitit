(ns reitit.ring.middleware.session
  (:require
   [clojure.spec.alpha :as s]
   [ring.middleware.session :as session]
   [ring.middleware.session.memory :as memory]))

(s/def ::spec (s/keys :opt-un [::session]))

(def ^:private store
  "The default shared in-memory session store.

  This is used when no `:session` key is provided to the middleware."
  (atom {}))

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
  | `:session`   | `ring.middleware.session.store/SessionStore` instance. Use `ring.middleware.session.memory/MemoryStore` by default."
  {:name    :session
   :spec    ::spec
   :compile (fn [{:keys [session]
                 :or   {session {:store (memory/memory-store store)}}} _]
              {:wrap #(session/wrap-session % session)})})
