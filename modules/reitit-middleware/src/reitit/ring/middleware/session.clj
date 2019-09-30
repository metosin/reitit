(ns reitit.ring.middleware.session
  (:require
   [ring.middleware.session :as session]
   [ring.middleware.session.memory :as memory]))

(def ^:private store
  "The in-memory session store.

  This is used when no `:session` key is provided to the middleware."
  (atom {}))

(def session-middleware
  "Middleware for session.

  Decodes the session from a request map into the `:session` value and updates the session store
  based on the response's `:session` value.

  | key          | description |
  | -------------|-------------|
  | `:session`   | `ring.middleware.session.store/SessionStore` instance. Use `ring.middleware.session.memory/MemoryStore` by default."
  {:name    :session
   :compile (fn [{:keys [session] :or {session {:store (memory/memory-store store)}}} _]
              {:wrap #(session/wrap-session % session)})})
