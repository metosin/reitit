# Reverse routing with Ring

Both the `router` and the `match` are injected into Ring Request (as `::r/router` and `::r/match`) by the `reitit.ring/ring-handler` and with that, available to middleware and endpoints. To convert a `Match` into a path, one can use `r/match->path`, which optionally takes a map of query-parameters too.

Below is an example how to do reverse routing from a ring handler:

```clj
(require '[reitit.core :as r])
(require '[reitit.ring :as ring])

(def app
  (ring/ring-handler
    (ring/router
      [["/users"
        {:get (fn [{::r/keys [router]}]
                {:status 200
                 :body (for [i (range 10)]
                         {:uri (-> router
                                   (r/match-by-name ::user {:id i})
                                   ;; with extra query-params
                                   (r/match->path {:iso "möly"}))})})}]
       ["/users/:id"
        {:name ::user
         :get (constantly {:status 200, :body "user..."})}]])))

(app {:request-method :get, :uri "/users"})
; {:status 200,
;  :body ({:uri "/users/0?iso=m%C3%B6ly"}
;         {:uri "/users/1?iso=m%C3%B6ly"}
;         {:uri "/users/2?iso=m%C3%B6ly"}
;         {:uri "/users/3?iso=m%C3%B6ly"}
;         {:uri "/users/4?iso=m%C3%B6ly"}
;         {:uri "/users/5?iso=m%C3%B6ly"}
;         {:uri "/users/6?iso=m%C3%B6ly"}
;         {:uri "/users/7?iso=m%C3%B6ly"}
;         {:uri "/users/8?iso=m%C3%B6ly"}
;         {:uri "/users/9?iso=m%C3%B6ly"})}
```
