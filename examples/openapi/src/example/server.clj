(ns example.server
  (:require [reitit.ring :as ring]
            [reitit.ring.spec]
            [reitit.coercion.malli]
            [reitit.openapi :as openapi]
            [reitit.ring.malli]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [muuntaja.core :as m]))

(def app
  (ring/ring-handler
    (ring/router
      [["/openapi.json"
        {:get {:no-doc true
               :openapi {:info {:title "my-api"
                                :description "openapi3 docs with [malli](https://github.com/metosin/malli) and reitit-ring"
                                :version "0.0.1"}
                         ;; used in /secure APIs below
                         :components {:securitySchemes {"auth" {:type :apiKey
                                                                :in :header
                                                                :name "Example-Api-Key"}}}}
               :handler (openapi/create-openapi-handler)}}]

       ["/pizza"
        {:get {:summary "Fetch a pizza | Multiple content-types, multiple examples"
               :responses {200 {:content {"application/json" {:description "Fetch a pizza as json"
                                                              :schema [:map
                                                                       [:color :keyword]
                                                                       [:pineapple :boolean]]
                                                              :examples {:white {:description "White pizza with pineapple"
                                                                                 :value {:color :white
                                                                                         :pineapple true}}
                                                                         :red {:description "Red pizza"
                                                                               :value {:color :red
                                                                                       :pineapple false}}}}
                                          "application/edn" {:description "Fetch a pizza as edn"
                                                             :schema [:map
                                                                      [:color :keyword]
                                                                      [:pineapple :boolean]]
                                                             :examples {:red {:description "Red pizza with pineapple"
                                                                              :value (pr-str {:color :red :pineapple true})}}}}}}
               :handler (fn [_request]
                          {:status 200
                           :body {:color :red
                                  :pineapple true}})}
         :post {:summary "Create a pizza | Multiple content-types, multiple examples"
                :request {:content {"application/json" {:description "Create a pizza using json"
                                                        :schema [:map
                                                                 [:color :keyword]
                                                                 [:pineapple :boolean]]
                                                        :examples {:purple {:value {:color :purple
                                                                                    :pineapple false}}}}
                                    "application/edn" {:description "Create a pizza using EDN"
                                                       :schema [:map
                                                                [:color :keyword]
                                                                [:pineapple :boolean]]
                                                       :examples {:purple {:value (pr-str {:color :purple
                                                                                           :pineapple false})}}}}}
                ;; Need to list content types explicitly because we use :default in :responses
                :openapi/response-content-types ["application/json" "application/edn"]
                :responses {200 {:content {:default {:description "Success"
                                                     :schema [:map [:success :boolean]]
                                                     :example {:success true}}}}}
                :handler (fn [_request]
                           {:status 200
                            :body {:success true}})}}]


       ["/contact"
        {:get {:summary "Search for a contact | Customizing via malli properties"
               :parameters {:query [:map
                                    [:limit {:title "How many results to return? Optional."
                                             :optional true
                                             :json-schema/default 30
                                             :json-schema/example 10}
                                     int?]
                                    [:email {:title "Email address to search for"
                                             :json-schema/format "email"}
                                     string?]]}
               :responses {200 {:content {:default {:schema [:vector
                                                             [:map
                                                              [:name {:json-schema/example "Heidi"}
                                                               string?]
                                                              [:email {:json-schema/example "heidi@alps.ch"}
                                                               string?]]]}}}}
               :handler (fn [_request]
                          [{:name "Heidi"
                            :email "heidi@alps.ch"}])}}]

       ["/secure"
        {:tags #{"secure"}
         :openapi {:security [{"auth" []}]}}
        ["/get"
         {:get {:summary "endpoint authenticated with a header"
                :responses {200 {:body [:map [:secret :string]]}
                            401 {:body [:map [:error :string]]}}
                :handler (fn [request]
                           ;; In a real app authentication would be handled by middleware
                           (if (= "secret" (get-in request [:headers "example-api-key"]))
                             {:status 200
                              :body {:secret "I am a marmot"}}
                             {:status 401
                              :body {:error "unauthorized"}}))}}]]]

      {;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
       :validate reitit.ring.spec/validate
       :exception pretty/exception
       :data {:coercion reitit.coercion.malli/coercion
              :muuntaja m/instance
              :middleware [openapi/openapi-feature
                           ;; query-params & form-params
                           parameters/parameters-middleware
                           ;; content-negotiation
                           muuntaja/format-negotiate-middleware
                           ;; encoding response body
                           muuntaja/format-response-middleware
                           ;; exception handling
                           exception/exception-middleware
                           ;; decoding request body
                           muuntaja/format-request-middleware
                           ;; coercing response bodys
                           coercion/coerce-response-middleware
                           ;; coercing request parameters
                           coercion/coerce-request-middleware
                           ;; multipart
                           multipart/multipart-middleware]}})
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/"
         :config {:validatorUrl nil
                  :urls [{:name "openapi", :url "openapi.json"}]
                  :urls.primaryName "openapi"
                  :operationsSorter "alpha"}})
      (ring/create-default-handler))))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))

(comment
  (start))
