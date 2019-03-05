# Shared routes

As `reitit-core` works with both Clojure & ClojureScript, one can have a shared routing table for both the frontend and the backend application, using the [Clojure Common Files](https://clojure.org/guides/reader_conditionals).

For backend, you need to define a `:handler` for the request processing, for frontend, `:name` enables the use of [reverse routing](../basics/name_based_routing.md).

There are multiple options to use shared routing table.

## Using reader conditionals

```clj
;; define the handlers for clojure
#?(:clj (declare get-kikka))
#?(:clj (declare post-kikka))

;; :name for both, :handler just for clojure
(def routes
  ["/kikka"
   {:name ::kikka
    #?@(:clj [:get {:handler get-kikka}])
    #?@(:clj [:post {:handler post-kikka}])}])
```

## Using custom expander

raw-routes can have any non-sequential data as a route argument, which gets expanded using the `:expand` option given to the `reitit.core.router` function. It defaults to `reitit.core/expand` multimethod.

First, define the common routes (in a `.cljc` file):

```clj
(def routes
  [["/kikka" ::kikka]
   ["/bar" ::bar]])
```

Those can be used as-is from ClojureScript:

```clj
(require '[reitit.core :as r])

(def router
  (r/router routes))

(r/match-by-name router ::kikka)
;#Match{:template "/kikka"
;       :data {:name :user/kikka}
;       :result nil
;       :path-params nil
;       :path "/kikka"}
```

For the backend, we can use a custom-expander to expand the routes:

```clj
(require '[reitit.ring :as ring])
(require '[reitit.core :as r])

(defn my-expand [registry]
  (fn [data opts]
    (if (keyword? data)
      (some-> data
              registry
              (r/expand opts)
              (assoc :name data))
      (r/expand data opts))))

;; the handler functions
(defn get-kikka [_] {:status 200, :body "get"})
(defn post-kikka [_] {:status 200, :body "post"})
(defn bar [_] {:status 200, :body "bar"})

(def app
  (ring/ring-handler
    (ring/router
      [["/kikka" ::kikka]
       ["/bar" ::bar]]
      ;; use a custom expander
      {:expand (my-expand
                 {::kikka {:get get-kikka
                           :post post-kikka}
                  ::bar bar})})))

(app {:request-method :post, :uri "/kikka"})
; {:status 200, :body "post"}
```
