# Reverse routing with Ring

Both the `router` and the `match` are injected into Ring Request (as `::r/router` and `::r/match`) by the `reitit.ring/ring-handler` and with that, available to middleware and endpoints.

Below is an example how to use the `router` to do reverse routing from a ring handler:

```clj
(require '[reitit.core :as r])
(require '[reitit.ring :as ring])

(def app
  (ring/ring-handler
    (ring/router
      [["/users" {:get (fn [{:keys [::r/router]}]
                         {:status 200
                          :body (for [i (range 10)]
                                  {:uri (:path (r/match-by-name router ::user {:id i}))})})}]
       ["/users/:id" {:name ::user
                      :get (constantly {:status 200, :body "user..."})}]])))

(app {:request-method :get, :uri "/users"})
;{:status 200,
; :body [{:uri "/users/0"}
;        {:uri "/users/1"}
;        {:uri "/users/2"}
;        {:uri "/users/3"}
;        {:uri "/users/4"}
;        {:uri "/users/5"}
;        {:uri "/users/6"}
;        {:uri "/users/7"}
;        {:uri "/users/8"}
;        {:uri "/users/9"}]}
```
