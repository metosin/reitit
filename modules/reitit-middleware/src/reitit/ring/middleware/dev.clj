(ns reitit.ring.middleware.dev
  (:require [lambdaisland.deep-diff :as ddiff]
            [lambdaisland.deep-diff.printer :as printer]
            [puget.color :as color]
            [reitit.core :as r]))

(def printer
  (-> (printer/puget-printer)
      (assoc :width 70)
      (update :color-scheme merge {:middleware [:blue]})))

(defn diff-doc [stage name previous current]
  [:group
   [:span "--- " (str stage) (if name (color/document printer :middleware (str " " name " "))) "---" :break :break]
   [:nest (printer/format-doc (if previous (ddiff/diff previous current) current) printer)]
   :break])

(defn polish [request]
  (dissoc request ::r/match ::r/router ::original ::previous))

(defn printed-request [name {::keys [previous] :as request}]
  (printer/print-doc (diff-doc :request name (polish previous) (polish request)) printer)
  (-> request
      (update ::original (fnil identity request))
      (assoc ::previous request)))

(defn printed-response [name {::keys [previous] :as response}]
  (printer/print-doc (diff-doc :response name (polish previous) (polish response)) printer)
  (-> response
      (update ::original (fnil identity response))
      (assoc ::previous response)
      (cond-> (nil? name) (dissoc ::original ::previous))))

(defn print-diff-middleware
  ([]
   (print-diff-middleware nil))
  ([{:keys [name]}]
   {:name ::diff
    :wrap (fn [handler]
            (fn
              ([request]
               (printed-response name (handler (printed-request name request))))
              ([request respond raise]
               (handler (printed-request name request) (comp respond (partial printed-response name)) raise))))}))

(defn print-request-diffs
  "A middleware chain transformer that adds a request & response diff
  printer between all middleware."
  [chain]
  (reduce
    (fn [chain mw]
      (into chain [mw (print-diff-middleware (select-keys mw [:name]))]))
    [(print-diff-middleware)] chain))
