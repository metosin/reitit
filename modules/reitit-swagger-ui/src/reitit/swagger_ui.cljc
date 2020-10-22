(ns reitit.swagger-ui
  #?(:clj (:require [reitit.ring :as ring]
                    [jsonista.core :as j])))

#?(:clj
   (defn create-swagger-ui-handler
     "Creates a ring handler which can be used to serve swagger-ui.

     | key              | description |
     | -----------------|-------------|
     | :parameter       | optional name of the wildcard parameter, defaults to unnamed keyword `:`
     | :root            | optional resource root, defaults to `\"swagger-ui\"`
     | :url             | path to swagger endpoint, defaults to `/swagger.json`
     | :path            | optional path to mount the handler to. Works only if mounted outside of a router.
     | :config          | parameters passed to swaggger-ui as-is.

     See https://github.com/swagger-api/swagger-ui/blob/master/docs/usage/configuration.md
     for all available :config options.

     Examples:
     ```
     (swagger-ui/create-swagger-ui-handler)

     (swagger-ui/create-swagger-ui-handler
       {:path \"/swagger-ui\"
        :url \"/api/swagger.json\"
        :config {:validatorUrl nil}})
     ```"
     ([]
      (create-swagger-ui-handler nil))
     ([options]
      (let [config-json (fn [{:keys [url config]}] (j/write-value-as-string (merge config {:url url})))
            options (as-> options $
                          (update $ :root (fnil identity "swagger-ui"))
                          (update $ :url (fnil identity "/swagger.json"))
                          (assoc $ :paths {"/config.json" {:headers {"Content-Type" "application/json"}
                                                           :status 200
                                                           :body (config-json $)}}))]
        (ring/routes
          (ring/create-resource-handler options))))))
