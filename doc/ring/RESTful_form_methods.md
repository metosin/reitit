# RESTful form methods

When designing RESTful applications you will be doing a lot of "PATCH" and "DELETE" request, but  most browsers don't support methods other than "GET" and "POST" when it comes to submitting forms. 

There is a pattern to solve this (pioneered by Rails) using a hidden "_method" field in the form and swapping out the "POST" method for whatever is in that field.

We can do this with middleware in reitit like this: 
```clj
(defn- hidden-method
  [request]
  (keyword 
    (or (get-in request [:form-params "_method"])         ;; look for "_method" field in :form-params
        (get-in request [:multipart-params "_method"])))) ;; or in :multipart-params

(def wrap-hidden-method
  {:name ::wrap-hidden-method
   :wrap (fn [handler]
           (fn [request]
             (if-let [fm (and (= :post (:request-method request)) ;; if this is a :post request
                              (hidden-method request))]           ;; and there is a "_method" field 
               (handler (assoc request :request-method fm)) ;; replace :request-method
               (handler request))))})
```

And apply the middleware like this: 
```clj
(reitit.ring/ring-handler
  (reitit.ring/router ...)
  (reitit.ring/create-default-handler)
  {:middleware 
    [reitit.ring.middleware.parameters/parameters-middleware ;; needed to have :form-params in the request map
     reitit.ring.middleware.multipart/multipart-middleware   ;; needed to have :multipart-params in the request map
     wrap-hidden-method]}) ;; our hidden method wrapper
```
(NOTE: This middleware must be placed here and not inside the route data given to `reitit.ring/handler`. 
This is so that our middleware is applied before reitit matches the request with a specific handler using the wrong method.)
