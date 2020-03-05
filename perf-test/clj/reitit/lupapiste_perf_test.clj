(ns reitit.lupapiste-perf-test
  (:require [reitit.perf-utils :refer [bench!! handler valid-urls]]
            [reitit.core :as reitit]
            [reitit.ring :as ring]

            [bidi.bidi :as bidi]
            [compojure.core :as compojure]

            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.route.map-tree :as map-tree]
            [io.pedestal.http.route.router :as pedestal]))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro11,3
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(def commands
  #{:upsert-appeal
    :upsert-appeal-verdict
    :delete-appeal
    :delete-appeal-verdict
    :mark-seen
    :mark-everything-seen
    :upsert-application-handler
    :remove-application-handler
    :cancel-inforequest
    :cancel-application
    :cancel-application-authority
    :undo-cancellation
    :request-for-complement
    :cleanup-krysp
    :submit-application
    :refresh-ktj
    :save-application-drawings
    :create-application
    :add-operation
    :update-op-description
    :change-primary-operation
    :change-permit-sub-type
    :change-location
    :change-application-state
    :return-to-draft
    :change-warranty-start-date
    :change-warranty-end-date
    :add-link-permit
    :remove-link-permit-by-app-id
    :create-change-permit
    :create-continuation-period-permit
    :convert-to-application
    :add-bulletin-comment
    :move-to-proclaimed
    :move-to-verdict-given
    :move-to-final
    :save-proclaimed-bulletin
    :save-verdict-given-bulletin
    :set-municipality-hears-neighbors
    :archive-documents
    :mark-pre-verdict-phase-archived
    :save-asianhallinta-config
    :create-assignment
    :update-assignment
    :complete-assignment
    :bind-attachment
    :bind-attachments
    :set-attachment-type
    :approve-attachment
    :reject-attachment
    :reject-attachment-note
    :create-attachments
    :create-ram-attachment
    :delete-attachment
    :delete-attachment-version
    :upload-attachment
    :rotate-pdf
    :upsert-stamp-template
    :delete-stamp-template
    :stamp-attachments
    :sign-attachments
    :set-attachment-meta
    :set-attachment-not-needed
    :set-attachments-as-verdict-attachment
    :set-attachment-as-construction-time
    :set-attachment-visibility
    :convert-to-pdfa
    :invite-with-role
    :approve-invite
    :decline-invitation
    :remove-auth
    :change-auth
    :unsubscribe-notifications
    :subscribe-notifications
    :set-calendar-enabled-for-authority
    :create-calendar-slots
    :update-calendar-slot
    :delete-calendar-slot
    :add-reservation-type-for-organization
    :update-reservation-type
    :delete-reservation-type
    :reserve-calendar-slot
    :accept-reservation
    :decline-reservation
    :cancel-reservation
    :mark-reservation-update-seen
    :add-campaign
    :delete-campaign
    :change-email-init
    :change-email
    :can-target-comment-to-authority
    :can-mark-answered
    :add-comment
    :company-update
    :company-lock
    :company-user-update
    :company-user-delete
    :company-user-delete-all
    :company-invite-user
    :company-add-user
    :company-invite
    :company-cancel-invite
    :save-company-tags
    :update-application-company-notes
    :inform-construction-started
    :inform-construction-ready
    :copy-application
    :update-3d-map-server-details
    :set-3d-map-enabled
    :redirect-to-3d-map
    :create-archiving-project
    :submit-archiving-project
    :create-doc
    :remove-doc
    :set-doc-status
    :update-doc
    :update-task
    :remove-document-data
    :approve-doc
    :reject-doc
    :reject-doc-note
    :set-user-to-document
    :set-current-user-to-document
    :set-company-to-document
    :set-feature
    :remove-uploaded-file
    :create-foreman-application
    :update-foreman-other-applications
    :link-foreman-task
    :update-guest-authority-organization
    :remove-guest-authority-organization
    :invite-guest
    :toggle-guest-subscription
    :delete-guest-application
    :info-link-delete
    :info-link-reorder
    :info-link-upsert
    :mark-seen-organization-links
    :create-inspection-summary-template
    :delete-inspection-summary-template
    :modify-inspection-summary-template
    :set-inspection-summary-template-for-operation
    :create-inspection-summary
    :delete-inspection-summary
    :toggle-inspection-summary-locking
    :add-target-to-inspection-summary
    :edit-inspection-summary-target
    :remove-target-from-inspection-summary
    :set-target-status
    :set-inspection-date
    :approve-application
    :move-attachments-to-backing-system
    :parties-as-krysp
    :merge-details-from-krysp
    :application-to-asianhallinta
    :attachments-to-asianhallinta
    :order-verdict-attachment-prints
    :frontend-log
    :reset-frontend-log
    :new-verdict-template
    :set-verdict-template-name
    :save-verdict-template-draft-value
    :publish-verdict-template
    :toggle-delete-verdict-template
    :copy-verdict-template
    :save-verdict-template-settings-value
    :add-verdict-template-review
    :update-verdict-template-review
    :add-verdict-template-plan
    :update-verdict-template-plan
    :set-default-operation-verdict-template
    :upsert-phrase
    :delete-phrase
    :neighbor-add
    :neighbor-add-owners
    :neighbor-update
    :neighbor-remove
    :neighbor-send-invite
    :neighbor-mark-done
    :neighbor-response
    :change-urgency
    :add-authority-notice
    :add-application-tags
    :init-sign
    :cancel-sign
    :convert-to-normal-inforequests
    :update-organization
    :add-scope
    :create-organization
    :add-organization-link
    :update-organization-link
    :remove-organization-link
    :update-allowed-autologin-ips
    :set-organization-selected-operations
    :organization-operations-attachments
    :set-organization-app-required-fields-filling-obligatory
    :set-automatic-ok-for-attachments
    :set-organization-assignments
    :set-organization-inspection-summaries
    :set-organization-extended-construction-waste-report
    :set-organization-validate-verdict-given-date
    :set-organization-use-attachment-links-integration
    :set-organization-calendars-enabled
    :set-organization-boolean-attribute
    :set-organization-permanent-archive-start-date
    :set-organization-neighbor-order-email
    :set-organization-submit-notification-email
    :set-organization-inforequest-notification-email
    :set-organization-default-reservation-location
    :set-krysp-endpoint
    :set-kopiolaitos-info
    :save-vendor-backend-redirect-config
    :update-organization-name
    :save-organization-tags
    :update-map-server-details
    :update-user-layers
    :update-suti-server-details
    :section-toggle-enabled
    :section-toggle-operation
    :upsert-handler-role
    :toggle-handler-role
    :upsert-assignment-trigger
    :remove-assignment-trigger
    :update-docstore-info
    :browser-timing
    :create-application-from-previous-permit
    :screenmessages-add
    :screenmessages-reset
    :add-single-sign-on-key
    :update-single-sign-on-key
    :remove-single-sign-on-key
    :create-statement-giver
    :delete-statement-giver
    :request-for-statement
    :ely-statement-request
    :delete-statement
    :save-statement-as-draft
    :give-statement
    :request-for-statement-reply
    :save-statement-reply-as-draft
    :reply-statement
    :suti-toggle-enabled
    :suti-toggle-operation
    :suti-www
    :suti-update-id
    :suti-update-added
    :create-task
    :delete-task
    :approve-task
    :reject-task
    :review-done
    :mark-review-faulty
    :resend-review-to-backing-system
    :set-tos-function-for-operation
    :remove-tos-function-from-operation
    :set-tos-function-for-application
    :force-fix-tos-function-for-application
    :store-tos-metadata-for-attachment
    :store-tos-metadata-for-application
    :store-tos-metadata-for-process
    :set-myyntipalvelu-for-attachment
    :create-user
    :create-rest-api-user
    :update-user
    :applicant-to-authority
    :update-default-application-filter
    :save-application-filter
    :remove-application-filter
    :update-user-organization
    :remove-user-organization
    :update-user-roles
    :check-password
    :change-passwd
    :reset-password
    :admin-reset-password
    :set-user-enabled
    :login
    :impersonate-authority
    :register-user
    :confirm-account-link
    :retry-rakentajafi
    :remove-user-attachment
    :copy-user-attachments-to-application
    :remove-user-notification
    :notifications-update
    :check-for-verdict
    :new-verdict-draft
    :save-verdict-draft
    :publish-verdict
    :delete-verdict
    :sign-verdict
    :create-digging-permit})

(def cqrs-routes
  (mapv (fn [command] [(str "/command/" (name command)) {:post handler :name command}]) commands))

(def cqrs-routes-pedestal
  (map-tree/router
    (table/table-routes
      (mapv (fn [command] [(str "/command/" (name command)) :post handler :route-name command]) commands))))

(def cqrs-routes-bidi
  ["/command/" (into {} (mapv (fn [command] [(name command) command]) commands))])

(def cqrs-routes-compojure
  (apply
    compojure/routes
    (map (fn [command] (compojure/ANY (str "/command/" (name command)) [] handler)) commands)))

;; Method code too large!
#_(def cqrs-routes-ataraxy
    (ataraxy/compile
      (into {} (mapv (fn [command] [(str "/command/" (name command)) [command]]) commands))))

(comment

  (doseq [route (valid-urls (reitit/router cqrs-routes))]
    (let [app (ring/ring-handler (ring/router cqrs-routes))
          match (app {:uri route :request-method :post})]
      (if-not match
        (println route))))

  (doseq [route (valid-urls (reitit/router cqrs-routes))]
    (let [match (pedestal/find-route cqrs-routes-pedestal {:path-info route :request-method :post})]
      (if-not match
        (println route)))))

(defn bench-cqrs! []
  (let [routes cqrs-routes
        router (reitit/router cqrs-routes)
        reitit-f #(reitit/match-by-path router (:uri %))
        reitit-ring-f (ring/ring-handler (ring/router routes))
        pedestal-f (partial pedestal/find-route cqrs-routes-pedestal)
        compojure-f cqrs-routes-compojure
        bidi-f #(bidi/match-route cqrs-routes-bidi (:uri %))
        b! (partial bench!! routes (fn [path] {:request-method :post, :uri path, :path-info path}) false)]

    ;;  125ns
    ;;   29ns (fast-map)
    (b! "reitit" reitit-f)

    ;;  272ns
    ;;  219ns (fast-assoc)
    ;;  171ns (fast-map)
    ;;   95ns (refined test, don't check handler)
    (b! "reitit-ring" reitit-ring-f)

    ;;  172ns
    ;;  133ns (refined test)
    (b! "pedestal" pedestal-f)

    ;; 21972ns (refined test)
    (b! "compojure" compojure-f)

    ;; 62762ns (refined test)
    (b! "bidi" bidi-f)))

(comment
  (bench-cqrs!))
