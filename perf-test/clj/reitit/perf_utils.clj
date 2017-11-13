(ns reitit.perf-utils
  (:require [criterium.core :as cc]
            [clojure.string :as str]
            [reitit.core :as reitit]))

(defn raw-title [color s]
  (println (str color (apply str (repeat (count s) "#")) "\u001B[0m"))
  (println (str color s "\u001B[0m"))
  (println (str color (apply str (repeat (count s) "#")) "\u001B[0m")))

(def title (partial raw-title "\u001B[35m"))
(def suite (partial raw-title "\u001B[32m"))

(defmacro bench! [name & body]
  `(do
     (title ~name)
     (println ~@body)
     (cc/quick-bench ~@body)))

(defn valid-urls [router]
  (->>
    (for [name (reitit/route-names router)
          :let [match (reitit/match-by-name router name)
                params (if (reitit/partial-match? match)
                         (-> match :required (zipmap (range))))]]
      (:path (reitit/match-by-name router name params)))
    (into [])))

(defrecord Request [uri path-info request-method])

(defn bench-routes [routes req f]
  (let [router (reitit/router routes)
        urls (valid-urls router)
        random-url #(rand-nth urls)
        log-time #(let [now (System/nanoTime)] (%) (- (System/nanoTime) now))
        total 10000
        dropped (int (* total 0.45))]
    (mapv
      (fn [path]
        (let [request (map->Request (req path))
              time (int (* (first (:sample-mean (cc/quick-benchmark (dotimes [_ 1000] (f request)) {}))) 1e6))]
          (println path "=>" time "ns")
          [path time]))
      urls)))

(defn bench [routes req no-paths?]
  (let [routes (mapv (fn [[path name]]
                       (if no-paths?
                         [(str/replace path #"\:" "") name]
                         [path name])) routes)
        router (reitit/router routes)]
    (doseq [[path time] (bench-routes routes req #(reitit/match-by-path router %))]
      (println path "\t" time))))

;;
;; Perf tests
;;

(def handler (constantly {:status 200, :body "ok"}))

(defn bench!! [routes req verbose? name f]
  (println)
  (suite name)
  (println)
  (let [times (for [[path time] (bench-routes routes req f)]
                (do
                  (when verbose? (println (format "%7s" time) "\t" path))
                  time))]
    (title (str "average: " (int (/ (reduce + times) (count times)))))))

(def commands #{:upsert-appeal :upsert-appeal-verdict :delete-appeal :delete-appeal-verdict :mark-seen :mark-everything-seen :upsert-application-handler :remove-application-handler :cancel-inforequest :cancel-application :cancel-application-authority :undo-cancellation :request-for-complement :cleanup-krysp :submit-application :refresh-ktj :save-application-drawings :create-application :add-operation :update-op-description :change-primary-operation :change-permit-sub-type :change-location :change-application-state :return-to-draft :change-warranty-start-date :change-warranty-end-date :add-link-permit :remove-link-permit-by-app-id :create-change-permit :create-continuation-period-permit :convert-to-application :add-bulletin-comment :move-to-proclaimed :move-to-verdict-given :move-to-final :save-proclaimed-bulletin :save-verdict-given-bulletin :set-municipality-hears-neighbors :archive-documents :mark-pre-verdict-phase-archived :save-asianhallinta-config :create-assignment :update-assignment :complete-assignment :bind-attachment :bind-attachments :set-attachment-type :approve-attachment :reject-attachment :reject-attachment-note :create-attachments :create-ram-attachment :delete-attachment :delete-attachment-version :upload-attachment :rotate-pdf :upsert-stamp-template :delete-stamp-template :stamp-attachments :sign-attachments :set-attachment-meta :set-attachment-not-needed :set-attachments-as-verdict-attachment :set-attachment-as-construction-time :set-attachment-visibility :convert-to-pdfa :invite-with-role :approve-invite :decline-invitation :remove-auth :change-auth :unsubscribe-notifications :subscribe-notifications :set-calendar-enabled-for-authority :create-calendar-slots :update-calendar-slot :delete-calendar-slot :add-reservation-type-for-organization :update-reservation-type :delete-reservation-type :reserve-calendar-slot :accept-reservation :decline-reservation :cancel-reservation :mark-reservation-update-seen :add-campaign :delete-campaign :change-email-init :change-email :can-target-comment-to-authority :can-mark-answered :add-comment :company-update :company-lock :company-user-update :company-user-delete :company-user-delete-all :company-invite-user :company-add-user :company-invite :company-cancel-invite :save-company-tags :update-application-company-notes :inform-construction-started :inform-construction-ready :copy-application :update-3d-map-server-details :set-3d-map-enabled :redirect-to-3d-map :create-archiving-project :submit-archiving-project :create-doc :remove-doc :set-doc-status :update-doc :update-task :remove-document-data :approve-doc :reject-doc :reject-doc-note :set-user-to-document :set-current-user-to-document :set-company-to-document :set-feature :remove-uploaded-file :create-foreman-application :update-foreman-other-applications :link-foreman-task :update-guest-authority-organization :remove-guest-authority-organization :invite-guest :toggle-guest-subscription :delete-guest-application :info-link-delete :info-link-reorder :info-link-upsert :mark-seen-organization-links :create-inspection-summary-template :delete-inspection-summary-template :modify-inspection-summary-template :set-inspection-summary-template-for-operation :create-inspection-summary :delete-inspection-summary :toggle-inspection-summary-locking :add-target-to-inspection-summary :edit-inspection-summary-target :remove-target-from-inspection-summary :set-target-status :set-inspection-date :approve-application :move-attachments-to-backing-system :parties-as-krysp :merge-details-from-krysp :application-to-asianhallinta :attachments-to-asianhallinta :order-verdict-attachment-prints :frontend-log :reset-frontend-log :new-verdict-template :set-verdict-template-name :save-verdict-template-draft-value :publish-verdict-template :toggle-delete-verdict-template :copy-verdict-template :save-verdict-template-settings-value :add-verdict-template-review :update-verdict-template-review :add-verdict-template-plan :update-verdict-template-plan :set-default-operation-verdict-template :upsert-phrase :delete-phrase :neighbor-add :neighbor-add-owners :neighbor-update :neighbor-remove :neighbor-send-invite :neighbor-mark-done :neighbor-response :change-urgency :add-authority-notice :add-application-tags :init-sign :cancel-sign :convert-to-normal-inforequests :update-organization :add-scope :create-organization :add-organization-link :update-organization-link :remove-organization-link :update-allowed-autologin-ips :set-organization-selected-operations :organization-operations-attachments :set-organization-app-required-fields-filling-obligatory :set-automatic-ok-for-attachments :set-organization-assignments :set-organization-inspection-summaries :set-organization-extended-construction-waste-report :set-organization-validate-verdict-given-date :set-organization-use-attachment-links-integration :set-organization-calendars-enabled :set-organization-boolean-attribute :set-organization-permanent-archive-start-date :set-organization-neighbor-order-email :set-organization-submit-notification-email :set-organization-inforequest-notification-email :set-organization-default-reservation-location :set-krysp-endpoint :set-kopiolaitos-info :save-vendor-backend-redirect-config :update-organization-name :save-organization-tags :update-map-server-details :update-user-layers :update-suti-server-details :section-toggle-enabled :section-toggle-operation :upsert-handler-role :toggle-handler-role :upsert-assignment-trigger :remove-assignment-trigger :update-docstore-info :browser-timing :create-application-from-previous-permit :screenmessages-add :screenmessages-reset :add-single-sign-on-key :update-single-sign-on-key :remove-single-sign-on-key :create-statement-giver :delete-statement-giver :request-for-statement :ely-statement-request :delete-statement :save-statement-as-draft :give-statement :request-for-statement-reply :save-statement-reply-as-draft :reply-statement :suti-toggle-enabled :suti-toggle-operation :suti-www :suti-update-id :suti-update-added :create-task :delete-task :approve-task :reject-task :review-done :mark-review-faulty :resend-review-to-backing-system :set-tos-function-for-operation :remove-tos-function-from-operation :set-tos-function-for-application :force-fix-tos-function-for-application :store-tos-metadata-for-attachment :store-tos-metadata-for-application :store-tos-metadata-for-process :set-myyntipalvelu-for-attachment :create-user :create-rest-api-user :update-user :applicant-to-authority :update-default-application-filter :save-application-filter :remove-application-filter :update-user-organization :remove-user-organization :update-user-roles :check-password :change-passwd :reset-password :admin-reset-password :set-user-enabled :login :impersonate-authority :register-user :confirm-account-link :retry-rakentajafi :remove-user-attachment :copy-user-attachments-to-application :remove-user-notification :notifications-update :check-for-verdict :new-verdict-draft :save-verdict-draft :publish-verdict :delete-verdict :sign-verdict :create-digging-permit})

(def queries #{:comments
               :actions
               :allowed-actions
               :allowed-actions-for-category
               :admin-attachment-report
               :appeals
               :application
               :application-authorities
               :application-commenters
               :enable-accordions
               :party-document-names
               :application-submittable
               :inforequest-markers
               :change-application-state-targets
               :link-permit-required
               :app-matches-for-link-permits
               :all-operations-in
               :application-handlers
               :application-organization-handler-roles
               :application-organization-archive-enabled
               :application-bulletins
               :application-bulletin-municipalities
               :application-bulletin-states
               :bulletin
               :bulletin-versions
               :bulletin-comments
               :publish-bulletin-enabled
               :municipality-hears-neighbors-visible
               :applications-search
               :applications-search-default
               :applications-for-new-appointment-page
               :get-application-operations
               :applications
               :latest-applications
               :event-search
               :tasks-tab-visible
               :application-info-tab-visible
               :application-summary-tab-visible
               :application-verdict-tab-visible
               :document-states
               :archiving-operations-enabled
               :permanent-archive-enabled
               :application-in-final-archiving-state
               :asianhallinta-config
               :assignments-for-application
               :assignment-targets
               :assignments-search
               :assignment-count
               :assignments
               :assignment
               :bind-attachments-job
               :attachments
               :attachment
               :attachment-groups
               :attachments-filters
               :attachments-tag-groups
               :attachment-types
               :ram-linked-attachments
               :attachment-operations
               :stamp-templates
               :custom-stamps
               :stamp-attachments-job
               :signing-possible
               :set-attachment-group-enabled
               :invites
               :my-calendars
               :calendar
               :calendars-for-authority-admin
               :calendar-slots
               :reservation-types-for-organization
               :available-calendar-slots
               :application-calendar-config
               :calendar-actions-required
               :applications-with-appointments
               :my-reserved-slots
               :campaigns
               :campaign
               :company
               :company-users-for-person-selector
               :company-tags
               :companies
               :user-company-locked
               :company-search-user
               :remove-company-tag-ok
               :company-notes
               :enable-company-search
               :info-construction-status
               :copy-application-invite-candidates
               :application-copyable-to-location
               :application-copyable
               :source-application
               :user-is-pure-digitizer
               :digitizing-enabled
               :document
               :validate-doc
               :fetch-validation-errors
               :schemas
               :features
               :apply-fixture
               :foreman-history
               :foreman-applications
               :resolve-guest-authority-candidate
               :guest-authorities-organization
               :application-guests
               :guest-authorities-application-organization
               :get-link-account-token
               :info-links
               :organization-links
               :organization-inspection-summary-settings
               :inspection-summaries-for-application
               :get-building-info-from-wfs
               :external-api-enabled
               :integration-messages
               :ely-statement-types
               :frontend-log-entries
               :newest-version
               :verdict-templates
               :verdict-template-categories
               :verdict-template
               :verdict-template-settings
               :verdict-template-reviews
               :verdict-template-plans
               :default-operation-verdict-templates
               :organization-phrases
               :application-phrases
               :owners
               :application-property-owners
               :municipality-borders
               :active-municipalities
               :municipality-active
               :neighbor-application
               :authority-notice
               :find-sign-process
               :organization-by-user
               :all-attachment-types-by-user
               :organization-name-by-user
               :user-organizations-for-permit-type
               :user-organizations-for-archiving-project
               :organizations
               :allowed-autologin-ips-for-organization
               :organization-by-id
               :permit-types
               :municipalities-with-organization
               :municipalities
               :all-operations-for-organization
               :selected-operations-for-municipality
               :addable-operations
               :organization-details
               :krysp-config
               :kopiolaitos-config
               :get-organization-names
               :vendor-backend-redirect-config
               :remove-tag-ok
               :get-organization-tags
               :get-organization-areas
               :get-map-layers-data
               :municipality-for-property
               :property-borders
               :screenmessages
               :get-single-sign-on-keys
               :get-organizations-statement-givers
               :get-possible-statement-statuses
               :get-statement-givers
               :statement-replies-enabled
               :statement-is-replyable
               :authorized-for-requesting-statement-reply
               :statement-attachment-allowed
               :statements-after-approve-allowed
               :neighbors-statement-enabled
               :suti-admin-details
               :suti-operations
               :suti-application-data
               :suti-application-products
               :suti-pre-sent-state
               :task-types-for-application
               :review-can-be-marked-done
               :is-end-review
               :available-tos-functions
               :tos-metadata-schema
               :case-file-data
               :tos-operations-enabled
               :common-area-application
               :user
               :users
               :users-in-same-organizations
               :user-by-email
               :users-for-datatables
               :saved-application-filters
               :redirect-after-login
               :user-attachments
               :add-user-attachment-allowed
               :email-in-use
               :enable-foreman-search
               :calendars-enabled
               :verdict-attachment-type
               :selected-digging-operations-for-organization
               :ya-extensions
               :approve-ya-extension})
